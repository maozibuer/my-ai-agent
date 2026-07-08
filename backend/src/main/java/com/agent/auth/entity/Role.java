package com.agent.auth.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * User role enumeration.
 * The {@code code} field is persisted to the database via MyBatis-Plus {@code @EnumValue}.
 * The {@code displayName} field is serialized to JSON via {@code @JsonValue}.
 */
@Getter
public enum Role {

    ADMIN("ADMIN", "管理员"),
    USER("USER", "普通用户");

    /** Database column value, also used for role-based authority prefix */
    @EnumValue
    private final String code;

    /** Human-readable display name */
    @JsonValue
    private final String displayName;

    Role(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }
}
