package com.aitaskmanager.controller.tasks;

import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aitaskmanager.repository.dto.tasks.TaskRequest;
import com.aitaskmanager.repository.dto.tasks.TaskResponse;
import com.aitaskmanager.repository.dto.tasks.TaskTreeResponse;
import com.aitaskmanager.repository.model.Tasks;
import com.aitaskmanager.service.tasks.TaskService;
import com.aitaskmanager.util.LogUtil;
import com.aitaskmanager.util.RequestGuard;

/**
 * タスクに関連するAPIエンドポイントを提供するコントローラー
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    
    @Autowired
    private TaskService taskService;

    /**
     * ユーザー名に基づいてタスクを取得するエンドポイント
     *
     * @param authentication 認証情報
     * @return タスクのリスト
     */
    @GetMapping
    public List<TaskResponse> getTasks(Authentication authentication) {
        Integer userSid = RequestGuard.requireUserSid();
        LogUtil.controller(TaskController.class, "tasks.list", userSid, authentication != null ? authentication.getName() : null, "invoked");
        List<Tasks> tasks = taskService.getTasksByUserId(userSid);
        // すべて東京タイムゾーンで返す
        SimpleDateFormat dueSdf = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat dtSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        dueSdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        dtSdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        
        return tasks.stream().map(t -> {
            TaskResponse dto = new TaskResponse();
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
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 階層ツリー取得
     * 
     * @param authentication 認証情報
     * @return タスク階層ツリーのリスト
     */
    @GetMapping("/tree")
    public List<TaskTreeResponse> getTaskTree(Authentication authentication) {
        Integer userSid = RequestGuard.requireUserSid();
        LogUtil.controller(TaskController.class, "tasks.tree", userSid, authentication != null ? authentication.getName() : null, "invoked");
        return taskService.getTaskTree(userSid);
    }

    /**
     * タスクを作成するエンドポイント
     * 
     * @param request タスク作成リクエスト
     * @param authentication 認証情報
     * @return 作成されたタスク
     */
    @PostMapping
    public Tasks createTask(@RequestBody TaskRequest request, Authentication authentication) {
        Integer userSid = RequestGuard.requireUserSid();
        LogUtil.controller(TaskController.class, "tasks.create", userSid, authentication != null ? authentication.getName() : null, "invoked");
        return taskService.createTask(userSid, request);
    }

    /**
     * タスクを更新するエンドポイント
     * 
     * @param id タスクID
     * @param request タスク更新リクエスト
     * @param authentication 認証情報
     * @return 更新されたタスク
     */
    @PutMapping("/{id}")
    public ResponseEntity<Tasks> updateTask(@PathVariable("id") int id, @RequestBody TaskRequest request, Authentication authentication) {
        Integer userSid = RequestGuard.requireUserSid();
        LogUtil.controller(TaskController.class, "tasks.update id=" + id, userSid, authentication != null ? authentication.getName() : null, "invoked");
        Tasks updated = taskService.updateTask(id, request, userSid);
        return ResponseEntity.ok(updated);
    }

    /**
     * タスクを再細分化するエンドポイント
     * 
     * @param id タスクID
     * @param request タスク再細分化リクエスト
     * @param authentication 認証情報
     * @return 再細分化されたタスクツリーのリスト
     */
    @PostMapping("/{id}/redecompose")
    public List<TaskTreeResponse> redecompose(@PathVariable("id") int id, @RequestBody TaskRequest request, Authentication authentication) {
        Integer userSid = RequestGuard.requireUserSid();
        LogUtil.controller(TaskController.class, "tasks.redecompose id=" + id, userSid, authentication != null ? authentication.getName() : null, "invoked");
        return taskService.redecomposeTask(userSid, id, request);
    }

    /**
     * タスクを削除するエンドポイント
     * 
     * @param id タスクID
     * @param authentication 認証情報
     */
    @PostMapping("/{id}/decompose")
    public List<TaskTreeResponse> decompose(@PathVariable int id, @RequestBody TaskRequest request, Authentication authentication) {
        Integer userSid = RequestGuard.requireUserSid();
        LogUtil.controller(TaskController.class, "tasks.decompose id=" + id, userSid, authentication != null ? authentication.getName() : null, "invoked");
        return taskService.redecomposeTask(userSid, id, request);
    }


    /**
     * タスクを削除するエンドポイント
     * 
     * @param id タスクID
     * @param authentication 認証情報
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable("id") int id, Authentication authentication) {
        Integer userSid = RequestGuard.requireUserSid();
        LogUtil.controller(TaskController.class, "tasks.delete id=" + id, userSid, authentication != null ? authentication.getName() : null, "invoked");
        taskService.deleteTask(id, userSid);
        return ResponseEntity.noContent().build();
    }

}
