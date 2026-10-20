package com.skillrental.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Người dùng không có quyền thao tác trên đơn này → HTTP 403. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
