package com.skillrental.controller;
import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import org.springframework.web.server.ResponseStatusException;import java.util.*;
@RestControllerAdvice(assignableTypes={SupportManagementController.class,WorkDocumentController.class,ComplaintDetailController.class})
public class WorkflowErrorAdvice {
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Object> status(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"Không thể thực hiện thao tác.":ex.getReason()));}
 @ExceptionHandler(NoSuchElementException.class) public ResponseEntity<Object> missing(){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message","Không tìm thấy dữ liệu trong phạm vi yêu cầu."));}
}
