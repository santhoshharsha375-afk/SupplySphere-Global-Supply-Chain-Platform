package com.supplysphere.dataset;

import com.supplysphere.core.util.DomainException;
import com.supplysphere.core.util.Preconditions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Directory-backed corpus loader — Module 1 / CO1.
 *
 * <p>Every {@code .txt} file in a folder becomes one {@link Article}. The first
 * non-blank line of the file is the article title; the remaining lines are the
 * body. Files are read in file-name order so the corpus (and therefore every
 * ranked report built on it) is reproducible across machines.</p>
 */
public final class CorpusLoader {

    private static final String TEXT_SUFFIX = ".txt";

    private CorpusLoader() {
    }

    /** Loads every {@code .txt} article in {@code directory}, ordered by file name. */
    public static List<Article> load(Path directory) {
        Preconditions.requireNonNull(directory, "directory");
        if (!Files.isDirectory(directory)) {
            throw DomainException.notFound("corpus directory", directory);
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.list(directory)) {
            walk.filter(Files::isRegularFile)
                    .filter(CorpusLoader::isTextFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(files::add);
        } catch (IOException failure) {
            throw DomainException.illegalState(
                    "cannot read corpus directory " + directory + ": " + failure.getMessage());
        }
        if (files.isEmpty()) {
            throw new DomainException("EMPTY_COLLECTION",
                    "no " + TEXT_SUFFIX + " articles found in " + directory);
        }
        List<Article> articles = new ArrayList<>(files.size());
        for (Path file : files) {
            articles.add(read(file));
        }
        return List.copyOf(articles);
    }

    /** Reads one article file: first non-blank line is the title, the rest the body. */
    public static Article read(Path file) {
        Preconditions.requireNonNull(file, "file");
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw DomainException.illegalState(
                    "cannot read article " + file + ": " + failure.getMessage());
        }
        int first = 0;
        while (first < lines.size() && lines.get(first).trim().isEmpty()) {
            first++;
        }
        String name = file.getFileName().toString();
        String title = first < lines.size() ? lines.get(first).trim() : name;
        StringBuilder body = new StringBuilder();
        for (int i = first + 1; i < lines.size(); i++) {
            body.append(lines.get(i)).append('\n');
        }
        return new Article(idOf(name), title, body.toString().trim());
    }

    private static boolean isTextFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(TEXT_SUFFIX);
    }

    private static String idOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
