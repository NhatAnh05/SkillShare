package com.skillrental.controller;
import com.skillrental.service.*;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Controller;import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;import org.springframework.security.core.Authentication;import java.util.*;import org.springframework.http.*;
@Controller @RequiredArgsConstructor
public class WorkDocumentController {
 private final CurrentUserService current;private final WorkDocumentService documents;private final PrivateFileStorage storage;
 @GetMapping("/orders/{id}/documents") @ResponseBody public Object orderList(@PathVariable Long id,@RequestParam(defaultValue="0") int page,Authentication a){return documents.list(id,null,current.require(a),page);}
 @GetMapping("/chat/{id}/documents") @ResponseBody public Object conversationList(@PathVariable Long id,@RequestParam(defaultValue="0") int page,Authentication a){return documents.list(null,id,current.require(a),page);}
 @PostMapping("/orders/{id}/documents") @ResponseBody public Object orderUpload(@PathVariable Long id,Authentication a,@RequestParam String description,@RequestParam(required=false) List<String> links,@RequestParam(required=false) List<MultipartFile> files){return documents.upload(id,null,current.require(a),description,links,files);}
 @PostMapping("/chat/{id}/documents") @ResponseBody public Object chatUpload(@PathVariable Long id,Authentication a,@RequestParam String description,@RequestParam(required=false) List<String> links,@RequestParam(required=false) List<MultipartFile> files){return documents.upload(null,id,current.require(a),description,links,files);}
 @GetMapping("/documents/{id}/download") public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id,Authentication a){var d=documents.require(id,current.require(a));return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header("X-Content-Type-Options","nosniff").header("Cache-Control","no-store").header("Content-Disposition",ContentDisposition.attachment().filename(d.getOriginalName(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(storage.resource(d.getStorageKey()));}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseBody @ResponseStatus(HttpStatus.BAD_REQUEST) public Object invalid(IllegalArgumentException ex){return Map.of("message",ex.getMessage()==null?"Tài liệu không hợp lệ.":ex.getMessage());}
 @ExceptionHandler(IllegalStateException.class) @ResponseBody @ResponseStatus(HttpStatus.CONFLICT) public Object unavailable(IllegalStateException ex){return Map.of("message",ex.getMessage());}
}
