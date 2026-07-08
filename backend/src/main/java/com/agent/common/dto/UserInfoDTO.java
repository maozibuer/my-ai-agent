package com.agent.common.dto;

import lombok.Data;

/**
 * DTO representing authenticated user information.
 */
@Data
public class UserInfoDTO {

    /** User ID */
    private Long id;

    /** Username */
    private String username;

    /** Email address */
    private String email;

    /** Role name */
    private String role;

    /** Avatar URL */
    private String avatar;
}
