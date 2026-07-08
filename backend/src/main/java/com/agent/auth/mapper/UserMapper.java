package com.agent.auth.mapper;

import com.agent.auth.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus mapper interface for {@link User} entities.
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * Selects a user by username, excluding logically deleted records.
     *
     * @param username the username to search for
     * @return the matching User, or null if not found
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    User selectByUsername(String username);
}
