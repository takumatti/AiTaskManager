package com.aitaskmanager.service.tasks;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.aitaskmanager.security.AuthUtils;

import com.aitaskmanager.repository.customMapper.TaskMapper;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.dto.tasks.TaskRequest;
import com.aitaskmanager.repository.dto.tasks.TaskTreeResponse;
import com.aitaskmanager.repository.customMapper.CustomAiUsageMapper;
import com.aitaskmanager.repository.generator.SubscriptionPlansMapper;
import com.aitaskmanager.repository.model.SubscriptionPlans;
import com.aitaskmanager.repository.model.Tasks;
import com.aitaskmanager.repository.model.Users;
import com.aitaskmanager.util.TaskUtils;
import com.aitaskmanager.util.LogUtil;
import com.aitaskmanager.service.ai.OpenAiDecomposeService;

import lombok.extern.slf4j.Slf4j;

/**
 * タスクに関連するビジネスロジックを提供するサービス
 */
@Service
@Slf4j
public class TaskService {
    
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private CustomAiUsageMapper customAiUsageMapper;

    @Autowired
    private SubscriptionPlansMapper subscriptionPlansMapper;

    @Autowired
    private OpenAiDecomposeService openAiDecomposeService;

    

    // OpenAI APIキーはapplication.propertiesから取得（環境変数依存を排除）
    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;
    
    // 一時停止用のフラグ（課金回避のため当面OFFにできる）
    @Value("${openai.enabled}")
    private boolean openaiEnabled;

    /**
     * ユーザーIDに基づいてタスクを取得する
     *
     * @param userSid ユーザーSID
     * @return タスクのリスト
     */
    public List<Tasks> getTasksByUserId(Integer userSid) {
        return taskMapper.selectByUserSid(userSid);
    }

    /**
     * タスクを作成する
     * 
     * @param userSid ユーザーSID
     * @param request タスク作成リクエスト
     * @return 作成されたタスク
     */
    @Transactional(rollbackFor = Exception.class)
    public Tasks createTask(Integer userSid, TaskRequest request) {
        LogUtil.service(TaskService.class, "tasks.create", "userSid=" + userSid, "started");
        // 受信ペイロードの要約ログ（原因特定用）
        log.debug("[TaskService] createTask payload userSid={} title='{}' priority={} status={} due_date={} ai_decompose={} parent_task_id={} parentTaskId={}",
            userSid,
            request.getTitle(),
            request.getPriority(),
            request.getStatus(),
            request.getDue_date(),
            request.getAi_decompose(),
            request.getParent_task_id(),
            request.getParentTaskId()
        );
        String reqTitle = TaskUtils.defaultString(request.getTitle(), "").trim();
        Tasks task = new Tasks();
        task.setUserSid(userSid);
        task.setTitle(reqTitle);
        task.setDescription(TaskUtils.defaultString(request.getDescription(), ""));
        task.setPriority(TaskUtils.normalizePriority(request.getPriority()));
        task.setStatus(TaskUtils.normalizeStatus(request.getStatus()));
        // 期限日: 任意だが、入力がある場合は存在日付チェック（例: 2026-02-30 は不可）
        java.sql.Date dueSql = TaskUtils.toSqlDate(request.getDue_date());
        if (request.getDue_date() != null && !request.getDue_date().isBlank() && dueSql == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在しない日付です");
        }
        task.setDueDate(dueSql);
        // 手動の子タスク作成: 親IDを許容（snake/camel両対応）
        Integer reqParentId = request.getParent_task_id() != null ? request.getParent_task_id() : request.getParentTaskId();
        log.debug("[TaskService] createTask parentId resolved={} userSid={}", reqParentId, userSid);
        if (reqParentId != null) {
            // 親の存在/権限を確認
            Tasks parent = taskMapper.selectByTaskSidAndUserSid(reqParentId, userSid);
            if (parent == null) {
                log.info("[TaskService] createTask parent not found or unauthorized reqParentId={} userSid={}", reqParentId, userSid);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "親タスクが見つかりません");
            }
            log.debug("[TaskService] createTask parent found parentSid={} userSid={}", parent.getTaskSid(), userSid);
            // 深さチェック（親が深さ4なら新規子は作れない）
            if (isMaxDepthReached(reqParentId, userSid)) {
                log.info("[TaskService] createTask reject due to depth limit (>=4) reqParentId={} userSid={}", reqParentId, userSid);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "階層は最大4までです");
            }
            task.setParentTaskSid(reqParentId);
        }
        try {
            taskMapper.insert(task); // useGeneratedKeysでID確定
        } catch (Exception ex) {
            // 予期せぬDB例外の詳細（入力値込み）を記録して再スロー
            log.error("[TaskService] createTask insert failed userSid={} title='{}' priority={} status={} due_date={} parentId={}", 
                userSid, task.getTitle(), task.getPriority(), task.getStatus(), request.getDue_date(), reqParentId, ex);
            throw ex;
        }
        log.debug("[TaskService] createTask parent inserted taskSid={} parentTaskSid={} (should be null for root)", task.getTaskSid(), task.getParentTaskSid());

        // 仕様変更: 作成時（POST /api/tasks）に ai_decompose=true でも子タスクの自動生成は行わない。
        // 子タスクの生成は『プレビュー→選択保存』フローに統一するため、ここでは親のみ作成して返す。
        if (Boolean.TRUE.equals(request.getAi_decompose())) {
            log.info("[TaskService] ai_decompose=true on create: 自動生成は行いません（プレビュー→選択保存で実施） parentSid={} userSid={}", task.getTaskSid(), userSid);
        }
        Tasks result = taskMapper.selectByTaskSidAndUserSid(task.getTaskSid(), userSid);
        LogUtil.service(TaskService.class, "tasks.create", "taskSid=" + result.getTaskSid() + " userSid=" + userSid, "completed");
    return result;
    }

    /**
     * タスクを更新する
     * 
     * @param taskSid タスクSID
     * @param request タスク更新リクエスト
     * @param userSid ユーザーSID
     * @return 更新されたタスク
     */
    @Transactional(rollbackFor = Exception.class)
    public Tasks updateTask(int taskSid, TaskRequest request, Integer userSid) {
        LogUtil.service(TaskService.class, "tasks.update", "taskSid=" + taskSid + " userSid=" + userSid, "started");
        // 既存タスクを取得し parentTaskId を保持（親子関係の喪失防止）
        Tasks existing = taskMapper.selectByTaskSidAndUserSid(taskSid, userSid);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "更新対象のタスクが存在しません");
        }
        String reqTitleUpdate = TaskUtils.defaultString(request.getTitle(), "").trim();

        Tasks task = new Tasks();
        task.setTaskSid(taskSid);
        task.setUserSid(userSid);
        task.setTitle(reqTitleUpdate);
        task.setDescription(TaskUtils.defaultString(request.getDescription(), ""));
        task.setPriority(TaskUtils.normalizePriority(request.getPriority()));
        task.setStatus(TaskUtils.normalizeStatus(request.getStatus()));
        java.sql.Date dueSqlUpdate = TaskUtils.toSqlDate(request.getDue_date());
        if (request.getDue_date() != null && !request.getDue_date().isBlank() && dueSqlUpdate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在しない日付です");
        }
        task.setDueDate(dueSqlUpdate);
        task.setParentTaskSid(existing.getParentTaskSid());

        int update = taskMapper.update(task);

        if (update == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが見つからないか権限がありません");
        }

        Tasks result = taskMapper.selectByTaskSidAndUserSid(taskSid, userSid);
        LogUtil.service(TaskService.class, "tasks.update", "taskSid=" + taskSid + " userSid=" + userSid, "completed");
        return result;
    }


    /**
     * タスクを削除する
     * 
     * @param taskSid タスクSID
     * @param userSid ユーザーSID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(int taskSid, Integer userSid) {
        LogUtil.service(TaskService.class, "tasks.delete", "taskSid=" + taskSid + " userSid=" + userSid, "started");
        try {
            if (userSid == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
            }

            // 再帰的にサブツリー削除（子→孫→...→親の順）
            deleteSubtree(taskSid, userSid);
            LogUtil.service(TaskService.class, "tasks.delete", "taskSid=" + taskSid + " userSid=" + userSid, "completed");
        } catch (ResponseStatusException ex) {
            // 既に意味のあるステータス/メッセージが設定されているのでそのまま投げ直す
            throw ex;
        } catch (Exception ex) {
            log.error("[Service] tasks.delete unexpected-error taskSid={} userSid={}", taskSid, userSid, ex);
            // 予期せぬ例外はメッセージを一般化して返す（フロントが表示可能）
            String msg = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "削除に失敗しました";
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        }
    }

    /**
     * タスクのサブツリーを再帰的に削除する
     * 
     * @param parentSid 親タスクSID
     * @param userSid ユーザーSID
     */
    private void deleteSubtree(int parentSid, Integer userSid) {
        // 直下の子を取得
        List<Tasks> children = taskMapper.selectChildrenByParentSid(parentSid);
        log.debug("[TaskService] deleteSubtree parentSid={} userSid={} childrenCount={}", parentSid, userSid, (children != null ? children.size() : 0));
        for (Tasks ch : children) {
            // 子のサブツリーを先に削除
            deleteSubtree(ch.getTaskSid(), userSid);
        }
        // 直下の子は既に削除済みなので最後に親を削除
        int deleted = taskMapper.deleteByTaskSidAndUserSid(parentSid, userSid);
        log.debug("[TaskService] deleteSubtree delete parentSid={} userSid={} deletedRows={}", parentSid, userSid, deleted);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが見つからないか権限がありません");
        }
        log.info("[TaskService] delete subtree node deleted parentSid={}", parentSid);
    }

    /**
     * 指定した親タスク配下（直下〜全孫以降すべて）の子孫タスクを再帰的に削除する。
     * 親自身は削除しない。
     * 
     * @param parentSid 親タスクSID
     * @param userSid ユーザーSID
     */
    private void deleteDescendants(int parentSid, Integer userSid) {
        List<Tasks> children = taskMapper.selectChildrenByParentSid(parentSid);
        for (Tasks ch : children) {
            deleteSubtree(ch.getTaskSid(), userSid);
        }
    }
    
    /**
     * ルートからの深さを1始まりで計算する（root=1, 子=2, 孫=3, ひ孫=4）
     * 
     * @param taskSid タスクSID
     * @param userSid ユーザーSID
     * @return 深さ
     */
    private int getDepthFromRoot(Integer taskSid, Integer userSid) {
        int depth = 0;
        Integer currentSid = taskSid;
        // 念のためループ上限を設定して循環を防止
        int guard = 0;
        while (currentSid != null && guard++ < 128) {
            Tasks t = taskMapper.selectByTaskSidAndUserSid(currentSid, userSid);
            if (t == null) break;
            depth++;
            currentSid = t.getParentTaskSid();
        }
        return depth;
    }

    /**
     * タスクの親からの深さが上限に達しているか確認する
     * 
     * @param parentTaskSid 親タスクSID
     * @param userSid ユーザーSID
     * @return 深さ上限に達している場合はtrue、そうでなければfalse
     */
    private boolean isMaxDepthReached(Integer parentTaskSid, Integer userSid) {
        int depth = getDepthFromRoot(parentTaskSid, userSid);
        return depth >= 4; // root=1, 子=2, 孫=3, ひ孫=4 まで許容
    }


    /**
     * 階層ツリー取得
     * 
     * @param userSid ユーザーSID
     * @return タスク階層ツリーのリスト
     */
    public List<TaskTreeResponse> getTaskTree(Integer userSid) {
        List<Tasks> all = taskMapper.selectByUserSid(userSid);
        // ルート（parentTaskId null）を起点に再帰構築
        return buildTree(all, null);
    }

    /**
     * タスク階層ツリーを再帰的に構築する
     * 
     * @param all すべてのタスク
     * @param parentSid 親タスクSID（nullの場合はルート）
     * @return タスク階層ツリーのリスト
     */
    private List<TaskTreeResponse> buildTree(List<Tasks> all, Integer parentSid) {
        SimpleDateFormat dueSdf = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat dtSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        dueSdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        dtSdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        return all.stream()
            .filter(t -> (parentSid == null && t.getParentTaskSid() == null) || (parentSid != null && parentSid.equals(t.getParentTaskSid())))
            .map(t -> {
                com.aitaskmanager.repository.dto.tasks.TaskTreeResponse dto = new com.aitaskmanager.repository.dto.tasks.TaskTreeResponse();
                dto.setId(t.getTaskSid());
                dto.setUserId(t.getUserSid());
                dto.setParentTaskId(t.getParentTaskSid());
                dto.setTitle(t.getTitle());
                dto.setDescription(t.getDescription());
                dto.setDueDate(t.getDueDate() != null ? dueSdf.format(t.getDueDate()) : null);
                dto.setPriority(t.getPriority());
                dto.setStatus(t.getStatus());
                dto.setCreatedAt(dtSdf.format(t.getCreatedAt()));
                dto.setUpdatedAt(dtSdf.format(t.getUpdatedAt()));
                dto.setDecomposedAt(t.getDecomposedAt() != null ? dtSdf.format(t.getDecomposedAt()) : null);
                dto.setChildren(buildTree(all, t.getTaskSid()));
                return dto;
            }).toList();
    }

    /**
     * タスクの再細分化を行う
     * 
     * @param userSid ユーザーSID
     * @param taskSid タスクSID
     * @param request タスクリクエスト
     * @return 最新のタスク階層ツリーのリスト
     */
    @Transactional(rollbackFor = Exception.class)
    public List<TaskTreeResponse> redecomposeTask(Integer userSid, Integer taskSid, TaskRequest request) {
        LogUtil.service(TaskService.class, "tasks.redecompose", "taskSid=" + taskSid + " userSid=" + userSid, "started");
        // 権限確認
        Tasks parent = taskMapper.selectByTaskSidAndUserSid(taskSid, userSid);
        if (parent == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが存在しません");
        // 深さ上限チェック（4階層まで）。親がすでに深さ4なら子の再生成は不可。
        if (isMaxDepthReached(taskSid, userSid)) {
            log.info("[TaskService] skip redecompose due to depth limit (>=4). parentSid={}", taskSid);
            return getTaskTree(userSid);
        }
        // 既存子孫を再帰削除（親は残す）
        deleteDescendants(taskSid, userSid);
        log.info("[TaskService] redecompose delete descendants done parentSid={}", taskSid);
        // プランのAIクォータを確認
        enforceAiQuotaOrThrow(userSid);

        String baseTitle = TaskUtils.defaultString(request.getTitle(), parent.getTitle()).trim();
        String baseDesc = TaskUtils.defaultString(request.getDescription(), parent.getDescription()).trim();
        if (baseDesc.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "説明が空のため再細分化できません");
        }

        List<String> items = openAiDecomposeService.decompose(baseDesc);
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AIで再細分化できませんでした。説明を具体的にしてください");
        }

        int maxChildren = 12;
        int children = Math.min(items.size(), maxChildren);
        for (int i = 0; i < children; i++) {
            String item = items.get(i);
            Tasks child = new Tasks();
            child.setUserSid(userSid);
            child.setParentTaskSid(taskSid);
            child.setTitle((baseTitle.isEmpty() ? "タスク" : baseTitle) + " - 再分解" + (i + 1));
            child.setDescription(item);
            child.setPriority(TaskUtils.normalizePriority(request.getPriority() != null ? request.getPriority() : parent.getPriority()));
            child.setStatus("TODO");
            child.setDueDate(TaskUtils.toSqlDate(request.getDue_date() != null ? request.getDue_date() : (parent.getDueDate() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(parent.getDueDate()) : null)));
            taskMapper.insert(child);
        }
        // 親の細分化日時更新
        taskMapper.updateDecomposedAt(taskSid, userSid);
        // 利用回数をカウント
        incrementAiUsage(userSid);
        LogUtil.service(TaskService.class, "tasks.redecompose", "taskSid=" + taskSid + " userSid=" + userSid + " children=" + children, "completed");
        // 最新全体ツリー返却
        return getTaskTree(userSid);
    }

    /**
     * 指定ユーザーのAI利用クォータを確認し、超過している場合は例外をスローする
     * 
     * @param userSid ユーザーSID
     */
    private void enforceAiQuotaOrThrow(Integer userSid) {
        // まず SecurityContext の JWT クレームから plan_sid を取得
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Integer planId = AuthUtils.getPlanId(auth);
        if (planId == null) {
              String principalUserId = (auth != null) ? com.aitaskmanager.security.AuthUtils.getUserId(auth) : null;
            Users u = (principalUserId != null) ? userMapper.selectByUserId(principalUserId) : null;
            if (u == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
            }
            planId = u.getPlanId();
        }

        // デフォルトは無料: 0回（ai_quota が 4 のとき無制限）
        Integer aiQuota = 0;
        if (planId != null) {
            SubscriptionPlans plan = subscriptionPlansMapper.selectByPrimaryKey(planId);
            if (plan != null) aiQuota = plan.getAiQuota();
        }

        LocalDate now = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        Integer used = customAiUsageMapper.selectUsedCount(userSid, now.getYear(), now.getMonthValue());
        if (used == null) used = 0;
        Integer bonus = customAiUsageMapper.selectBonusCount(userSid, now.getYear(), now.getMonthValue());
        if (bonus == null) bonus = 0;

        // 無制限プラン（ai_quota が null または 4）は常に許可（API設定は別途チェック）
        boolean unlimited = (aiQuota == null) || (aiQuota.intValue() == 4);
        if (!unlimited) {
            int aiQuotaValue = aiQuota != null ? aiQuota.intValue() : 0; // null 安全
            int effectiveQuota = aiQuotaValue + bonus; // Free(0)でもボーナスがあれば許可
            if (effectiveQuota <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AIの利用可能回数がありません（プラン残＋ボーナス）");
            }
            if (used >= effectiveQuota) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "今月のAI利用回数上限に達しました（残り0）");
            }
        } // unlimited の場合は制限なし（used は内部計測のみ）

        // APIキー存在確認（OpenAI連携が有効であること）
        // application.propertiesのopenai.apiKeyを使用
        String apiKey = openaiApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI連携が未設定です（管理者へお問い合わせください）");
        }
    }

    /**
     * 指定ユーザーの当月のAI利用回数をインクリメントする
     * 
     * @param userSid ユーザーSID
     */
    private void incrementAiUsage(Integer userSid) {
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        customAiUsageMapper.upsertIncrement(userSid, now.getYear(), now.getMonthValue());
    }

    
}
