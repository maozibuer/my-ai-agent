package com.agent.common;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic paginated response wrapper.
 *
 * @param <T> the type of the records
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** List of records on the current page */
    private List<T> records;

    /** Total number of records */
    private long total;

    /** Current page number */
    private long current;

    /** Page size */
    private long size;

    /**
     * Factory method to create a PageResult.
     *
     * @param <T>       the type of the records
     * @param records   the list of records
     * @param total     the total number of records
     * @param current   the current page number
     * @param size      the page size
     * @return a PageResult instance
     */
    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return new PageResult<>(records, total, current, size);
    }
}
