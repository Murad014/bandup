package com.bandup.api.content.web;

import java.io.IOException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bandup.api.content.application.ImageLibrary;

/** {@code POST /api/v1/admin/media}: upload a cover image (ADMIN / AUTHOR; multipart field {@code file}). */
@RestController
@RequestMapping("/api/v1/admin/media")
@RequiredArgsConstructor
class AdminMediaController {

    private final ImageLibrary images;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ImageLibrary.UploadedImage upload(@RequestParam("file") MultipartFile file) throws IOException {
        return images.upload(file.getBytes(), file.getContentType());
    }
}
