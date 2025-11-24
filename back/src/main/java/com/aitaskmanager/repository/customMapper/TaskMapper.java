package com.aitaskmanager.repository.customMapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.aitaskmanager.repository.model.Tasks;

/**
 * タスクに関連するデータベース操作を定義するマッパーインターフェース
 */
@Mapper
public interface TaskMapper {
    
    /**
     * ユーザーIDに基づいてタスクを選択する
     *
     * @param userId ユーザーID
     * @return タスクのリスト
     */
    List<Tasks> selectByUserId(Integer userId);

    /**
     * タスクを挿入する
     * 
     * @param task タスクオブジェクト
     * @return 挿入された行数
     */
    int insert(Tasks task);

}
