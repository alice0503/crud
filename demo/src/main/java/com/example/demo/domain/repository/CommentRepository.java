package com.example.demo.domain.repository;

import com.example.demo.domain.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;



public interface CommentRepository extends JpaRepository<Comment, Long> {
}