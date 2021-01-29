package com.example.demo.service;

import com.example.demo.domain.repository.BoardRepository;
import com.example.demo.dto.BoardDto;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;


@Service
public class BoardService {
    private BoardRepository boardRepository;

    public BoardService(BoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    @Transactional
    public Long savePost(BoardDto boardDto) {
        return boardRepository.save(boardDto.toEntity()).getId();
    }
}