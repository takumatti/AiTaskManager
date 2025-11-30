package com.aitaskmanager.service.tasks;

import java.util.List;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aitaskmanager.util.SecurityUtils;

import com.aitaskmanager.repository.customMapper.TaskMapper;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.dto.login.tasks.TaskRequest;
import com.aitaskmanager.repository.model.Tasks;
import com.aitaskmanager.repository.model.Users;
import com.aitaskmanager.util.TaskUtils;
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

    /**
     * ユーザー名に基づいてタスクを取得する
     *
     * @param username ユーザー名
     * @return タスクのリスト
     */
    public List<Tasks> getTasksByUsername(String username) {
        Integer userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            log.info("[TaskService] userId not found in request attributes. Fallback DB lookup username={}", username);
            Users user = userMapper.selectByUserName(username);
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
            }
            userId = user.getId();
        }
        return taskMapper.selectByUserId(userId);
    }

    /**
     * タスクを作成する
     * 
     * @param username ユーザー名
     * @param request タスク作成リクエスト
     * @return 作成されたタスク
     */
    @Transactional(rollbackFor = Exception.class)
    public Tasks createTask(String username, TaskRequest request) {

        Integer userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            log.info("[TaskService] createTask fallback userId lookup username={}", username);
            Users user = userMapper.selectByUserName(username);
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
            }
            userId = user.getId();
        }

        Tasks task = new Tasks();
        task.setUserId(userId);
        task.setTitle(TaskUtils.defaultString(request.getTitle(), ""));
        task.setDescription(TaskUtils.defaultString(request.getDescription(), ""));
        task.setPriority(TaskUtils.normalizePriority(request.getPriority()));
        task.setStatus(TaskUtils.normalizeStatus(request.getStatus()));
        task.setDueDate(TaskUtils.toSqlDate(request.getDue_date()));
        taskMapper.insert(task); // 親タスク挿入でID確定
        log.debug("[TaskService] createTask parent inserted id={} parentTaskId={} (should be null for root)", task.getId(), task.getParentTaskId());

        if (Boolean.TRUE.equals(request.getAi_decompose())) {
            generateChildTasks(task.getId(), userId, request, "create");
        }
        return task;
    }

    /**
     * タスクを更新する
     * 
     * @param taskId タスクID
     * @param request タスク更新リクエスト
     * @param username ユーザー名
     * @return 更新されたタスク
     */
    @Transactional(rollbackFor = Exception.class)
    public Tasks updateTask(int taskId, TaskRequest request, String username) {

        Integer userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            log.info("[TaskService] updateTask fallback userId lookup username={}", username);
            userId = userMapper.selectIdByUsername(username);
        }
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
        }

        // 既存タスクを取得し parentTaskId を保持（親子関係の喪失防止）
        Tasks existing = taskMapper.selectByTaskIdAndUserId(taskId, userId);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "更新対象のタスクが存在しません");
        }

        Tasks task = new Tasks();
        task.setId(taskId);
        task.setUserId(userId);
        task.setTitle(TaskUtils.defaultString(request.getTitle(), ""));
        task.setDescription(TaskUtils.defaultString(request.getDescription(), ""));
        task.setPriority(TaskUtils.normalizePriority(request.getPriority()));
        task.setStatus(TaskUtils.normalizeStatus(request.getStatus()));
        task.setDueDate(TaskUtils.toSqlDate(request.getDue_date()));
        task.setParentTaskId(existing.getParentTaskId());

        int update = taskMapper.update(task);

        if (update == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが見つからないか権限がありません");
        }

        // 更新時にもAI細分化要求がある場合、当該タスクを親として子タスクを生成（暫定ダミー）
        if (Boolean.TRUE.equals(request.getAi_decompose())) {
            generateChildTasks(taskId, userId, request, "update");
        }

        Tasks result = taskMapper.selectByTaskIdAndUserId(taskId, userId);
        log.debug("[TaskService] updateTask completed id={} parentTaskId={} ai_decompose={}", taskId, result.getParentTaskId(), request.getAi_decompose());
        return result;
    }


    /**
     * タスクを削除する
     * 
     * @param taskId タスクID
     * @param username ユーザー名
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(int taskId, String username) {

        Integer userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            log.info("[TaskService] deleteTask fallback userId lookup username={}", username);
            userId = userMapper.selectIdByUsername(username);
        }
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
        }

        // 再帰的にサブツリー削除（子→孫→...→親の順）
        deleteSubtree(taskId, userId);
    }

    /**
     * タスクのサブツリーを再帰的に削除する
     * 
     * @param parentId 親タスクID
     * @param userId ユーザーID
     */
    private void deleteSubtree(int parentId, Integer userId) {
        // 直下の子を取得
        List<Tasks> children = taskMapper.selectChildrenByParentId(parentId);
        for (Tasks ch : children) {
            // 子のサブツリーを先に削除
            deleteSubtree(ch.getId(), userId);
        }
        // 直下の子は既に削除済みなので最後に親を削除
        int deleted = taskMapper.deleteByIdAndUserId(parentId, userId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが見つからないか権限がありません");
        }
        log.info("[TaskService] delete subtree node deleted id={}", parentId);
    }
    
    /**
     * ルートからの深さを1始まりで計算する（root=1, 子=2, 孫=3, ひ孫=4）
     * 
     * @param taskId タスクID
     * @param userId ユーザーID
     * @return 深さ
     */
    private int getDepthFromRoot(Integer taskId, Integer userId) {
        int depth = 0;
        Integer currentId = taskId;
        // 念のためループ上限を設定して循環を防止
        int guard = 0;
        while (currentId != null && guard++ < 128) {
            Tasks t = taskMapper.selectByTaskIdAndUserId(currentId, userId);
            if (t == null) break;
            depth++;
            currentId = t.getParentTaskId();
        }
        return depth;
    }

    /**
     * タスクの親からの深さが上限に達しているか確認する
     * 
     * @param parentTaskId 親タスクID
     * @param userId ユーザーID
     * @return 深さ上限に達している場合はtrue、そうでなければfalse
     */
    private boolean isMaxDepthReached(Integer parentTaskId, Integer userId) {
        int depth = getDepthFromRoot(parentTaskId, userId);
        return depth >= 4; // root=1, 子=2, 孫=3, ひ孫=4 まで許容
    }

    /**
     * 指定親タスクの下に子タスクを生成する（AI細分化のダミー実装）
     * 
     * @param parentTaskId 親タスクID
     * @param userId ユーザーID
     * @param request タスクリクエスト
     * @param mode 呼び出しモード（create/update）
     */
    private void generateChildTasks(Integer parentTaskId, Integer userId, TaskRequest request, String mode) {
        try {
            // 深さ上限チェック（4階層まで）
            if (isMaxDepthReached(parentTaskId, userId)) {
                log.info("[TaskService] skip ai_decompose due to depth limit (>=4). parentId={}", parentTaskId);
                return; // 親の更新/作成は成功させつつ子生成は行わない
            }
            // 既存子タスクがある場合は二重生成回避
            int existingChildren = taskMapper.countChildrenByParentId(parentTaskId);
            if (existingChildren > 0) {
                log.info("[TaskService] skip ai_decompose because children already exist parentId={} existingChildren={}", parentTaskId, existingChildren);
                return;
            }
            String baseTitle = TaskUtils.defaultString(request.getTitle(), "");
            String baseDesc = TaskUtils.defaultString(request.getDescription(), "");
            String[] parts = baseDesc.split("\n\n");
            int children = Math.max(1, Math.min(3, parts.length));
            for (int i = 0; i < children; i++) {
                Tasks child = new Tasks();
                child.setUserId(userId);
                child.setParentTaskId(parentTaskId);
                child.setTitle((baseTitle.isEmpty() ? "タスク" : baseTitle) + " - サブタスク" + (i + 1));
                child.setDescription(i < parts.length ? parts[i] : baseDesc);
                child.setPriority(TaskUtils.normalizePriority(request.getPriority()));
                child.setStatus("TODO");
                child.setDueDate(TaskUtils.toSqlDate(request.getDue_date()));
                taskMapper.insert(child);
                log.debug("[TaskService] child inserted id={} parentTaskId={} mode={}", child.getId(), child.getParentTaskId(), mode);
            }
            log.info("[TaskService] ai_decompose {} children generated parentId={}", children, parentTaskId);
        } catch (Exception ex) {
            log.warn("[TaskService] ai_decompose {} failed parentId={} err={}", mode, parentTaskId, ex.getMessage());
        }
    }

    /**
     * 階層ツリー取得
     * 
     * @param username ユーザー名
     * @return タスク階層ツリーのリスト
     */
    public List<com.aitaskmanager.repository.dto.login.tasks.TaskTreeResponse> getTaskTree(String username) {
        Integer userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            Users user = userMapper.selectByUserName(username);
            if (user == null){
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
            }
            userId = user.getId();
        }
        List<Tasks> all = taskMapper.selectByUserId(userId);
        // ルート（parentTaskId null）を起点に再帰構築
        return buildTree(all, null);
    }

    /**
     * タスク階層ツリーを再帰的に構築する
     * 
     * @param all すべてのタスク
     * @param parentId 親タスクID（nullの場合はルート）
     * @return タスク階層ツリーのリスト
     */
    private List<com.aitaskmanager.repository.dto.login.tasks.TaskTreeResponse> buildTree(List<Tasks> all, Integer parentId) {
        SimpleDateFormat dueSdf = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat dtSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        dueSdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        dtSdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        return all.stream()
            .filter(t -> (parentId == null && t.getParentTaskId() == null) || (parentId != null && parentId.equals(t.getParentTaskId())))
            .map(t -> {
                com.aitaskmanager.repository.dto.login.tasks.TaskTreeResponse dto = new com.aitaskmanager.repository.dto.login.tasks.TaskTreeResponse();
                dto.setId(t.getId());
                dto.setUserId(t.getUserId());
                dto.setParentTaskId(t.getParentTaskId());
                dto.setTitle(t.getTitle());
                dto.setDescription(t.getDescription());
                dto.setDueDate(t.getDueDate() != null ? dueSdf.format(t.getDueDate()) : null);
                dto.setPriority(t.getPriority());
                dto.setStatus(t.getStatus());
                dto.setCreatedAt(dtSdf.format(t.getCreatedAt()));
                dto.setUpdatedAt(dtSdf.format(t.getUpdatedAt()));
                dto.setDecomposedAt(t.getDecomposedAt() != null ? dtSdf.format(t.getDecomposedAt()) : null);
                dto.setChildren(buildTree(all, t.getId()));
                return dto;
            }).toList();
    }

    /**
     * タスクの再細分化を行う
     * 
     * @param username ユーザー名
     * @param taskId タスクID
     * @param request タスクリクエスト
     * @return 最新のタスク階層ツリーのリスト
     */
    @Transactional(rollbackFor = Exception.class)
    public List<com.aitaskmanager.repository.dto.login.tasks.TaskTreeResponse> redecomposeTask(String username, Integer taskId, TaskRequest request) {
        Integer userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            Users user = userMapper.selectByUserName(username);
            if (user == null){
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザーが存在しません");
            }
            userId = user.getId();
        }
        // 権限確認
        Tasks parent = taskMapper.selectByTaskIdAndUserId(taskId, userId);
        if (parent == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが存在しません");
        // 深さ上限チェック（4階層まで）。親がすでに深さ4なら子の再生成は不可。
        if (isMaxDepthReached(taskId, userId)) {
            log.info("[TaskService] skip redecompose due to depth limit (>=4). parentId={}", taskId);
            return getTaskTree(username);
        }
        // 既存子削除
        int deleted = taskMapper.deleteChildrenByParentId(taskId);
        log.info("[TaskService] redecompose delete children count={} parentId={}", deleted, taskId);
        // 再生成（existingChildrenチェックはスキップするため直接ロジック）
        String baseTitle = TaskUtils.defaultString(request.getTitle(), parent.getTitle());
        String baseDesc = TaskUtils.defaultString(request.getDescription(), parent.getDescription());
        String[] parts = baseDesc.split("\n\n");
        int children = Math.max(1, Math.min(3, parts.length));
        for (int i = 0; i < children; i++) {
            Tasks child = new Tasks();
            child.setUserId(userId);
            child.setParentTaskId(taskId);
            child.setTitle((baseTitle.isEmpty() ? "タスク" : baseTitle) + " - 再分解" + (i + 1));
            child.setDescription(i < parts.length ? parts[i] : baseDesc);
            child.setPriority(TaskUtils.normalizePriority(request.getPriority() != null ? request.getPriority() : parent.getPriority()));
            child.setStatus("TODO");
            child.setDueDate(TaskUtils.toSqlDate(request.getDue_date() != null ? request.getDue_date() : (parent.getDueDate() != null ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(parent.getDueDate()) : null)));
            taskMapper.insert(child);
        }
        // 親の細分化日時更新
        taskMapper.updateDecomposedAt(taskId, userId);
        log.info("[TaskService] redecompose regenerated children={} parentId={}", children, taskId);
        // 最新全体ツリー返却
        return getTaskTree(username);
    }
}
