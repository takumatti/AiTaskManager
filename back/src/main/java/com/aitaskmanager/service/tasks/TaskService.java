package com.aitaskmanager.service.tasks;

import java.sql.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aitaskmanager.repository.customMapper.TaskMapper;
import com.aitaskmanager.repository.customMapper.UserMapper;
import com.aitaskmanager.repository.dto.login.tasks.TaskRequest;
import com.aitaskmanager.repository.model.Tasks;
import com.aitaskmanager.repository.model.Users;

/**
 * タスクに関連するビジネスロジックを提供するサービス
 */
@Service
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

        Users user = userMapper.selectByUserName(username);

        if (user == null) {
            throw new RuntimeException("ユーザーが存在しません: " + username);
        }

        return taskMapper.selectByUserId(user.getId());
    }

    /**
     * タスクを作成する
     * 
     * @param username ユーザー名
     * @param req タスク作成リクエスト
     * @return 作成されたタスク
     */
    @Transactional(rollbackFor = Exception.class)
    public Tasks createTask(String username, TaskRequest req) {

        Users user = userMapper.selectByUserName(username);
        if (user == null) {
            throw new RuntimeException("ユーザーが存在しません");
        }

        Tasks task = new Tasks();
        task.setUserId(user.getId());
        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription());
        task.setPriority(req.getPriority());

        if (req.getDue_date() != null && !req.getDue_date().isEmpty()) {
            task.setDueDate(Date.valueOf(req.getDue_date()));
        }

        taskMapper.insert(task);

        return task;
    }

}
