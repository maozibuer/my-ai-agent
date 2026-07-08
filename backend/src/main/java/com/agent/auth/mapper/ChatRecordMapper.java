package com.agent.auth.mapper;

import java.util.List;

import com.agent.auth.entity.ChatRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus mapper interface for {@link ChatRecord} entities.
 */
@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {

    /**
     * Selects all chat records for a given session, ordered by creation time.
     *
     * @param sessionId the chat session identifier
     * @return a list of ChatRecord objects for the session
     */
    @Select("SELECT * FROM chat_record WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY create_time ASC")
    List<ChatRecord> selectBySessionId(@Param("sessionId") String sessionId);
}
