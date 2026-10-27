package com.example.aichat.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.file.config.FileStorageProperties;
import com.example.aichat.file.dto.FileQueryRequest;
import com.example.aichat.file.entity.FileMetadata;
import com.example.aichat.file.enums.FileStatus;
import com.example.aichat.file.exception.FileErrorCode;
import com.example.aichat.file.mapper.FileMetadataMapper;
import com.example.aichat.file.service.FileService;
import com.example.aichat.file.storage.ObjectStorage;
import com.example.aichat.file.vo.FileDownloadUrlVO;
import com.example.aichat.file.vo.FileMetadataVO;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FileServiceImpl implements FileService {
    private static final int MAX_FILE_COUNT = 10;
    private static final DateTimeFormatter OBJECT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileMetadataMapper mapper;
    private final ObjectStorage storage;
    private final CurrentUserProvider currentUserProvider;
    private final FileStorageProperties properties;

    public FileServiceImpl(FileMetadataMapper mapper, ObjectStorage storage,
                           CurrentUserProvider currentUserProvider, FileStorageProperties properties) {
        this.mapper = mapper;
        this.storage = storage;
        this.currentUserProvider = currentUserProvider;
        this.properties = properties;
    }

    @Override
    public FileMetadataVO upload(MultipartFile file) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        FileMetadata metadata = prepareMetadata(user, file);
        putObject(file, metadata);
        try {
            mapper.insert(metadata);
        } catch (RuntimeException exception) {
            try {
                storage.delete(metadata.bucketName, metadata.objectKey);
            } catch (RuntimeException ignored) {
                // Preserve the database error while the object cleanup can be retried separately.
            }
            throw exception;
        }
        return toVO(metadata);
    }

    @Override
    public List<FileMetadataVO> uploadBatch(List<MultipartFile> files) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        if (files == null || files.isEmpty()) throw error(FileErrorCode.FILE_EMPTY);
        if (files.size() > MAX_FILE_COUNT) throw error(FileErrorCode.FILE_COUNT_EXCEEDED);

        List<FileMetadata> metadataList = files.stream()
                .map(file -> prepareMetadata(user, file))
                .toList();
        List<FileMetadata> uploaded = new ArrayList<>(metadataList.size());
        List<FileMetadata> inserted = new ArrayList<>(metadataList.size());
        try {
            for (int index = 0; index < files.size(); index++) {
                FileMetadata metadata = metadataList.get(index);
                putObject(files.get(index), metadata);
                uploaded.add(metadata);
                mapper.insert(metadata);
                inserted.add(metadata);
            }
        } catch (RuntimeException exception) {
            cleanupBatch(uploaded, inserted);
            throw exception;
        }
        return inserted.stream().map(this::toVO).toList();
    }

    @Override
    public FileMetadataVO findAccessible(Long fileId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        FileMetadata metadata = findRequired(fileId);
        requireOwner(metadata, user.id());
        requireActive(metadata);
        return toVO(metadata);
    }

    @Override
    public InputStreamResource download(Long fileId) {
        FileMetadata metadata = findAccessibleMetadata(fileId);
        return new InputStreamResource(storage.get(metadata.bucketName, metadata.objectKey));
    }

    @Override
    public FileDownloadUrlVO createDownloadUrl(Long fileId) {
        FileMetadata metadata = findAccessibleMetadata(fileId);
        long expiresIn = properties.getPresignExpireSeconds();
        String url = storage.createPresignedGetUrl(metadata.bucketName, metadata.objectKey,
                Duration.ofSeconds(expiresIn));
        return new FileDownloadUrlVO(metadata.id, url, expiresIn);
    }

    @Override
    public void deleteOwned(Long fileId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        FileMetadata metadata = findRequired(fileId);
        requireOwner(metadata, user.id());
        delete(metadata);
    }

    @Override
    public PageResult<FileMetadataVO> page(FileQueryRequest request) {
        QueryWrapper<FileMetadata> query = new QueryWrapper<>();
        if (request.getUserId() != null) query.eq("user_id", request.getUserId());
        if (StringUtils.hasText(request.getOriginalName())) query.like("original_name", request.getOriginalName());
        if (StringUtils.hasText(request.getContentType())) query.eq("content_type", request.getContentType());
        if (request.getStatus() != null) query.eq("status", request.getStatus().name());
        if (request.getCreatedFrom() != null) {
            query.ge("create_time", request.getCreatedFrom().atStartOfDay());
        }
        if (request.getCreatedTo() != null) {
            query.lt("create_time", request.getCreatedTo().plusDays(1).atStartOfDay());
        }
        query.orderByDesc("create_time").orderByDesc("id");
        long page = request.getPage() == null ? 1 : Math.max(1, request.getPage());
        long size = request.getSize() == null ? 20 : Math.min(Math.max(1, request.getSize()), 100);
        Page<FileMetadata> result = mapper.selectPage(new Page<>(page, size), query);
        return PageResult.from(result, this::toVO);
    }

    @Override
    public FileMetadataVO findByAdmin(Long fileId) {
        return toVO(findRequired(fileId));
    }

    @Override
    public void deleteByAdmin(Long fileId) {
        delete(findRequired(fileId));
    }

    @Override
    public List<FileMetadataVO> validateOwnedActiveFiles(Long userId, List<Long> fileIds) {
        List<Long> uniqueIds = uniqueIds(fileIds);
        if (uniqueIds.size() > MAX_FILE_COUNT) throw error(FileErrorCode.FILE_COUNT_EXCEEDED);
        if (uniqueIds.isEmpty()) return List.of();
        Map<Long, FileMetadata> records = byIds(uniqueIds);
        List<FileMetadataVO> result = new ArrayList<>(uniqueIds.size());
        for (Long fileId : uniqueIds) {
            FileMetadata metadata = records.get(fileId);
            if (metadata == null) throw error(FileErrorCode.FILE_NOT_FOUND);
            requireOwner(metadata, userId);
            requireActive(metadata);
            result.add(toVO(metadata));
        }
        return result;
    }

    @Override
    public List<FileMetadataVO> findByFileIds(Long userId, List<Long> fileIds) {
        List<Long> uniqueIds = uniqueIds(fileIds);
        if (uniqueIds.isEmpty()) return List.of();
        Map<Long, FileMetadata> records = byIds(uniqueIds);
        List<FileMetadataVO> result = new ArrayList<>();
        for (Long fileId : uniqueIds) {
            FileMetadata metadata = records.get(fileId);
            if (metadata != null && metadata.userId.equals(userId) && metadata.status == FileStatus.ACTIVE) {
                result.add(toVO(metadata));
            }
        }
        return result;
    }

    private FileMetadata prepareMetadata(CurrentUser user, MultipartFile file) {
        validateUpload(file);
        String originalName = safeOriginalName(file.getOriginalFilename());
        if (originalName.length() > 255) throw error(FileErrorCode.FILE_NAME_TOO_LONG);

        FileMetadata metadata = new FileMetadata();
        metadata.userId = user.id();
        metadata.bucketName = properties.getBucket();
        metadata.objectKey = objectKey(user.id(), originalName);
        metadata.originalName = originalName;
        metadata.contentType = file.getContentType();
        metadata.fileSize = file.getSize();
        metadata.status = FileStatus.ACTIVE;
        metadata.deleted = 0;
        return metadata;
    }

    private void putObject(MultipartFile file, FileMetadata metadata) {
        try (InputStream inputStream = file.getInputStream()) {
            storage.put(metadata.bucketName, metadata.objectKey, inputStream,
                    file.getSize(), file.getContentType());
        } catch (IOException exception) {
            throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR.errorCode(),
                    "Unable to read uploaded file");
        }
    }

    private void cleanupBatch(List<FileMetadata> uploaded, List<FileMetadata> inserted) {
        for (FileMetadata metadata : inserted) {
            try {
                mapper.deleteById(metadata.id);
            } catch (RuntimeException ignored) {
            }
        }
        for (FileMetadata metadata : uploaded) {
            try {
                storage.delete(metadata.bucketName, metadata.objectKey);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw error(FileErrorCode.FILE_EMPTY);
        if (properties.getMaxFileSize() > 0 && file.getSize() > properties.getMaxFileSize()) {
            throw error(FileErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        boolean allowed = contentType != null && properties.getAllowedContentTypes().stream()
                .anyMatch(value -> value.equalsIgnoreCase(contentType));
        if (!allowed) throw error(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        validateSignature(file, contentType);
    }

    private void validateSignature(MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(8);
            boolean valid = switch (contentType.toLowerCase(Locale.ROOT)) {
                case "application/pdf" -> startsWith(header, new byte[]{'%', 'P', 'D', 'F', '-'});
                case "image/jpeg" -> startsWith(header, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
                case "image/png" -> startsWith(header,
                        new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
                case "image/gif" -> startsWith(header, new byte[]{'G', 'I', 'F', '8'});
                case "application/msword" -> startsWith(header,
                        new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> startsWith(header,
                        new byte[]{'P', 'K', 0x03, 0x04});
                default -> true;
            };
            if (!valid) throw error(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        } catch (IOException exception) {
            throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR.errorCode(), "Unable to read uploaded file");
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private FileMetadata findAccessibleMetadata(Long fileId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        FileMetadata metadata = findRequired(fileId);
        requireOwner(metadata, user.id());
        requireActive(metadata);
        return metadata;
    }

    private FileMetadata findRequired(Long fileId) {
        if (fileId == null) throw error(FileErrorCode.FILE_NOT_FOUND);
        FileMetadata metadata = mapper.selectById(fileId);
        if (metadata == null) throw error(FileErrorCode.FILE_NOT_FOUND);
        return metadata;
    }

    private void requireOwner(FileMetadata metadata, Long userId) {
        if (!metadata.userId.equals(userId)) throw error(FileErrorCode.FILE_ACCESS_DENIED);
    }

    private void requireActive(FileMetadata metadata) {
        if (metadata.status != FileStatus.ACTIVE) throw error(FileErrorCode.FILE_NOT_FOUND);
    }

    private void delete(FileMetadata metadata) {
        if (metadata.status == FileStatus.DELETED) return;
        metadata.status = FileStatus.DELETING;
        mapper.updateById(metadata);
        try {
            storage.delete(metadata.bucketName, metadata.objectKey);
        } catch (RuntimeException exception) {
            metadata.status = FileStatus.DELETE_FAILED;
            mapper.updateById(metadata);
            throw exception;
        }
        metadata.status = FileStatus.DELETED;
        metadata.deleted = 1;
        mapper.updateById(metadata);
    }

    private Map<Long, FileMetadata> byIds(List<Long> fileIds) {
        return mapper.selectBatchIds(fileIds).stream()
                .collect(java.util.stream.Collectors.toMap(metadata -> metadata.id, metadata -> metadata));
    }

    private List<Long> uniqueIds(List<Long> fileIds) {
        if (fileIds == null) return List.of();
        if (fileIds.stream().anyMatch(java.util.Objects::isNull)) throw error(FileErrorCode.FILE_NOT_FOUND);
        return fileIds.stream().distinct().toList();
    }

    private String objectKey(Long userId, String originalName) {
        String extension = extension(originalName);
        return "users/" + userId + "/" + LocalDate.now().format(OBJECT_DATE_FORMAT) + "/"
                + java.util.UUID.randomUUID() + "." + extension;
    }

    private String extension(String originalName) {
        int dot = originalName.lastIndexOf('.');
        if (dot < 1 || dot == originalName.length() - 1) return "bin";
        String extension = originalName.substring(dot + 1).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return extension.isBlank() ? "bin" : extension.substring(0, Math.min(extension.length(), 20));
    }

    private String safeOriginalName(String originalName) {
        if (!StringUtils.hasText(originalName)) return "file";
        String normalized = originalName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        name = name.replaceAll("[\\p{Cntrl}]", "_");
        return StringUtils.hasText(name) ? name : "file";
    }

    private FileMetadataVO toVO(FileMetadata metadata) {
        return new FileMetadataVO(metadata.id, metadata.userId, metadata.originalName,
                metadata.contentType, metadata.fileSize, metadata.status,
                metadata.createTime, metadata.updateTime);
    }

    private BusinessException error(FileErrorCode code) {
        return new BusinessException(code.errorCode(), code.message());
    }
}
