package com.aitaskmanager.service.tasks;

import java.util.List;

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
        taskMapper.insert(task);
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

        Tasks task = new Tasks();
        task.setId(taskId);
        task.setUserId(userId);
        task.setTitle(TaskUtils.defaultString(request.getTitle(), ""));
        task.setDescription(TaskUtils.defaultString(request.getDescription(), ""));
        task.setPriority(TaskUtils.normalizePriority(request.getPriority()));
        task.setStatus(TaskUtils.normalizeStatus(request.getStatus()));
        task.setDueDate(TaskUtils.toSqlDate(request.getDue_date()));

        int update = taskMapper.update(task);


        if (update == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが見つからないか権限がありません");
        }

        Tasks result = taskMapper.selectByTaskIdAndUserId(taskId, userId);

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

        int deleted = taskMapper.deleteByIdAndUserId(taskId, userId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "タスクが見つからないか権限がありません");
        }
    }

}
