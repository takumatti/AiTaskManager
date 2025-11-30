package com.aitaskmanager.repository.customMapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * タスクIDとユーザーIDに基づいてタスクを選択する
     * 
     * @param taskId タスクID
     * @param userId ユーザーID
     * @return タスクオブジェクト
     */
    Tasks selectByTaskIdAndUserId(@Param("taskId") Integer taskId, 
                                  @Param("userId")Integer userId);

    /**
     * タスクを挿入する
     * 
     * @param task タスクオブジェクト
     * @return 挿入された行数
     */
    int insert(Tasks task);

    /**
     * タスクを更新する
     * 
     * @param task タスクオブジェクト
     * @return 更新された行数
     */
    int update(Tasks task);

    /**
     * タスクIDとユーザーIDに基づいてタスクを削除する
     * 
     * @param id タスクID
     * @param userId ユーザーID
     * @return 削除された行数
     */
    int deleteByIdAndUserId(@Param("id") Integer id, 
                            @Param("userId") Integer userId);

    /**
     * 親タスクIDに紐づく子タスク件数を取得する（二重細分化防止用）
     * 
     * @param parentTaskId 親タスクID
     * @return 子タスク件数
     */
    int countChildrenByParentId(@Param("parentTaskId") Integer parentTaskId);

    /**
     * 親タスクIDで子タスク一覧取得
     * 
     * @param parentTaskId 親タスクID
     * @return 子タスクリスト
     */
    List<Tasks> selectChildrenByParentId(@Param("parentTaskId") Integer parentTaskId);

    /**
     * 親タスクの細分化日時更新
     * 
     * @param id 親タスクID
     * @param userId ユーザーID
     */
    int updateDecomposedAt(@Param("id") Integer id, @Param("userId") Integer userId);
}
