package com.example.loopa.domain.archive.service;

import com.example.loopa.domain.archive.repository.ArchiveViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveService {

    private final ArchiveViewRepository archiveViewRepository;
}
