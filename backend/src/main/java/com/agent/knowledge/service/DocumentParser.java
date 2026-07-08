package com.agent.knowledge.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.agent.common.BusinessException;
import com.agent.common.ResultCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for parsing text content from uploaded documents.
 * Supports PDF (via Apache PDFBox), Word (via Apache POI), and plain text files.
 */
@Slf4j
@Service
public class DocumentParser {

    /**
     * Parses a PDF file and extracts its text content.
     *
     * @param file the uploaded PDF file
     * @return the extracted text content
     */
    public String parsePdf(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                log.info("Parsed PDF file: {}, extracted {} characters", file.getOriginalFilename(), text.length());
                return text;
            }
        } catch (IOException e) {
            log.error("Failed to parse PDF file: {}", file.getOriginalFilename(), e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "Failed to parse PDF file: " + e.getMessage());
        }
    }

    /**
     * Parses a Word document (.docx) and extracts its text content.
     *
     * @param file the uploaded Word file
     * @return the extracted text content
     */
    public String parseWord(MultipartFile file) {
        try (InputStream inputStream = new ByteArrayInputStream(file.getBytes());
             XWPFDocument document = new XWPFDocument(inputStream)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            String text = extractor.getText();
            extractor.close();
            log.info("Parsed Word file: {}, extracted {} characters", file.getOriginalFilename(), text.length());
            return text;
        } catch (IOException e) {
            log.error("Failed to parse Word file: {}", file.getOriginalFilename(), e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "Failed to parse Word file: " + e.getMessage());
        }
    }

    /**
     * Parses a plain text file and returns its content as a string.
     *
     * @param file the uploaded text file
     * @return the text content
     */
    public String parseTxt(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            log.info("Parsed TXT file: {}, extracted {} characters", file.getOriginalFilename(), text.length());
            return text;
        } catch (IOException e) {
            log.error("Failed to parse TXT file: {}", file.getOriginalFilename(), e);
            throw new BusinessException(ResultCode.BAD_REQUEST, "Failed to parse TXT file: " + e.getMessage());
        }
    }

    /**
     * Dispatches parsing to the appropriate method based on the file extension.
     *
     * @param file the uploaded file
     * @return the extracted text content
     */
    public String parse(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "File name must have an extension");
        }
        try {
            return parseBytes(file.getBytes(), fileName);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Parses already-loaded file bytes, dispatching by extension.
     * Use this when the bytes have already been read (e.g. for hashing) to avoid
     * reading the stream twice.
     *
     * @param bytes    the raw file bytes
     * @param fileName the original file name (used to determine extension)
     * @return the extracted text content
     */
    public String parseBytes(byte[] bytes, String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "File name must have an extension");
        }
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        log.info("Parsing bytes for file: {} (extension: {})", fileName, ext);
        return switch (ext) {
            case "pdf"        -> parsePdfBytes(bytes, fileName);
            case "doc", "docx"-> parseWordBytes(bytes, fileName);
            case "txt", "md",
                 "csv"        -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            default -> throw new BusinessException(ResultCode.BAD_REQUEST,
                    "Unsupported file type: " + ext + ". Supported: pdf, doc, docx, txt, md, csv");
        };
    }

    private String parsePdfBytes(byte[] bytes, String fileName) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Parsed PDF {}: {} characters", fileName, text.length());
            return text;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Failed to parse PDF: " + e.getMessage());
        }
    }

    private String parseWordBytes(byte[] bytes, String fileName) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            log.info("Parsed Word {}: {} characters", fileName, text.length());
            return text;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Failed to parse Word: " + e.getMessage());
        }
    }
}
