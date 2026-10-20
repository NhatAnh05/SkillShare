package com.skillrental.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Chuyển trạng thái đơn không nằm trong bảng chuyển hợp lệ → HTTP 409. */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidOrderTransitionException extends RuntimeException {
    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}
