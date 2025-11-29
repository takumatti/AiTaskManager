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

import com.aitaskmanager.repository.dto.login.tasks.TaskRequest;
import com.aitaskmanager.repository.model.Tasks;
import com.aitaskmanager.repository.dto.login.tasks.TaskResponse;
import com.aitaskmanager.service.tasks.TaskService;

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
        String username = authentication.getName();
        List<Tasks> tasks = taskService.getTasksByUsername(username);
        // すべて東京タイムゾーンで返す
        SimpleDateFormat dueSdf = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat dtSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        dueSdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        dtSdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
        
        return tasks.stream().map(t -> {
            TaskResponse dto = new TaskResponse();
            dto.setId(t.getId());
            dto.setUser_id(t.getUserId());
            dto.setTitle(t.getTitle());
            dto.setDescription(t.getDescription());
            dto.setDue_date(t.getDueDate() != null ? dueSdf.format(t.getDueDate()) : null);
            dto.setPriority(t.getPriority());
            dto.setStatus(t.getStatus());
            dto.setCreated_at(dtSdf.format(t.getCreatedAt()));
            dto.setUpdated_at(dtSdf.format(t.getUpdatedAt()));
            return dto;
        }).collect(Collectors.toList());
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
        String username = authentication.getName();
        return taskService.createTask(username, request);
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
    public ResponseEntity<Tasks> updateTask(@PathVariable int id, @RequestBody TaskRequest request, Authentication authentication) {
        String username = authentication.getName();
        Tasks updated = taskService.updateTask(id, request, username);
        return ResponseEntity.ok(updated);
    }


    /**
     * タスクを削除するエンドポイント
     * 
     * @param id タスクID
     * @param authentication 認証情報
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable int id, Authentication authentication) {
        String username = authentication.getName();
        taskService.deleteTask(id, username);
        return ResponseEntity.noContent().build();
    }

}
