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
     * ユーザーSIDに基づいてタスクを選択する
     *
     * @param userSid ユーザーSID
     * @return タスクのリスト
     */
    List<Tasks> selectByUserSid(Integer userSid);

    /**
     * タスクSIDとユーザーSIDに基づいてタスクを選択する
     * 
     * @param taskSid タスクSID
     * @param userSid ユーザーSID
     * @return タスクオブジェクト
     */
    Tasks selectByTaskSidAndUserSid(@Param("taskSid") Integer taskSid, 
                                    @Param("userSid")Integer userSid);

    /**
     * 親タスクSIDに紐づく子タスク件数を取得する（二重細分化防止用）
     * 
     * @param parentTaskSid 親タスクSID
     * @return 子タスク件数
     */
    int countChildrenByParentSid(@Param("parentTaskSid") Integer parentTaskSid);

    /**
     * 親タスクSIDで子タスク一覧取得
     * 
     * @param parentTaskSid 親タスクSID
     * @return 子タスクリスト
     */
    List<Tasks> selectChildrenByParentSid(@Param("parentTaskSid") Integer parentTaskSid);

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
     * 親タスクの細分化日時更新
     * 
     * @param taskSid 親タスクSID
     * @param userSid ユーザーSID
     */
    int updateDecomposedAt(@Param("taskSid") Integer taskSid,
                           @Param("userSid") Integer userSid);

    /**
     * タスクSIDとユーザーSIDに基づいてタスクを削除する
     * 
     * @param taskSid タスクSID
     * @param userSid ユーザーSID
     * @return 削除された行数
     */
    int deleteByTaskSidAndUserSid(@Param("taskSid") Integer taskSid, 
                            @Param("userSid") Integer userSid);

    /**
     * 指定した親の直下にあるタスクSID一覧を取得
     */
    List<Integer> selectIdsByParent(@Param("userSid") Integer userSid,
                                    @Param("parentTaskSid") Integer parentTaskSid);

    /**
     * 指定リストの親IDにぶら下がるタスクSID一覧を取得
     */
    List<Integer> selectIdsByParents(@Param("userSid") Integer userSid,
                                     @Param("parentTaskSids") List<Integer> parentTaskSids);

    /**
     * 指定したタスクSID群を削除
     */
    int deleteByIds(@Param("userSid") Integer userSid,
                    @Param("taskSids") List<Integer> taskSids);

}
