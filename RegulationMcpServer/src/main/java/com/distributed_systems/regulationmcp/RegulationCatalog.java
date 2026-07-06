package com.distributed_systems.regulationmcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class RegulationCatalog {

	private static final Pattern ARTICLE = Pattern.compile(
			"(?ms)^\\s*Art\\.\\s*(\\d+)[ºo°]?\\.?\\s*(.*?)(?=^\\s*Art\\.\\s*\\d+|\\z)"
	);
	private static final Pattern SECTION = Pattern.compile(
			"(?m)^\\s*((?:T[IÍ]TULO|CAP[IÍ]TULO|SE[CÇ][AÃ]O)\\s+[^\\r\\n]+)"
	);

	private final RegulationProperties properties;
	private volatile Snapshot cached;

	RegulationCatalog(RegulationProperties properties) {
		this.properties = properties;
	}

	Snapshot snapshot() {
		Snapshot value = cached;
		if (value == null) {
			synchronized (this) {
				value = cached;
				if (value == null) {
					value = read();
					cached = value;
				}
			}
		}
		return value;
	}

	private Snapshot read() {
		Path corpusPath = properties.corpusPath();
		Path metadataPath = properties.metadataPath();
		if (!Files.isRegularFile(corpusPath) || !Files.isRegularFile(metadataPath)) {
			throw new IllegalStateException("The versioned regulation corpus is not available");
		}
		Metadata metadata = metadata(metadataPath);
		if (!checksum(corpusPath).equalsIgnoreCase(metadata.sha256())) {
			throw new IllegalStateException("Regulation checksum does not match metadata");
		}
		try (PDDocument pdf = Loader.loadPDF(corpusPath.toFile())) {
			PDFTextStripper stripper = new PDFTextStripper();
			StringBuilder text = new StringBuilder();
			List<Integer> pages = new ArrayList<>();
			for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
				pages.add(text.length());
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				text.append(stripper.getText(pdf)).append('\n');
			}
			String normalized = text.toString().replace("\u00ad", "").replace('\u00a0', ' ');
			List<Heading> headings = headings(normalized);
			Map<String, Article> articles = new LinkedHashMap<>();
			Matcher matcher = ARTICLE.matcher(normalized);
			while (matcher.find()) {
				String number = matcher.group(1);
				articles.put(number, new Article(
						number,
						sectionAt(headings, matcher.start()),
						pageAt(pages, matcher.start()),
						"Art. " + number + " " + matcher.group(2).replaceAll("\\s+", " ").trim()
				));
			}
			if (articles.isEmpty()) {
				throw new IllegalStateException("No articles were extracted from the regulation");
			}
			return new Snapshot(
					metadata,
					Map.copyOf(articles),
					headings.stream().map(Heading::text).distinct().toList()
			);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to read regulation PDF", exception);
		}
	}

	private Metadata metadata(Path path) {
		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(path)) {
			values.load(input);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to read corpus metadata", exception);
		}
		return new Metadata(
				required(values, "document.title"),
				required(values, "document.resolution"),
				required(values, "document.version"),
				required(values, "document.official-url"),
				required(values, "document.sha256"),
				Files.exists(properties.corpusPath()) ? properties.corpusPath().getFileName().toString() : ""
		);
	}

	private String required(Properties values, String key) {
		String value = values.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing corpus metadata: " + key);
		}
		return value.trim();
	}

	private String checksum(Path path) {
		try (InputStream input = Files.newInputStream(path)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			input.transferTo(new DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (IOException | NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Unable to verify regulation checksum", exception);
		}
	}

	private List<Heading> headings(String text) {
		List<Heading> headings = new ArrayList<>();
		Matcher matcher = SECTION.matcher(text);
		while (matcher.find()) {
			headings.add(new Heading(matcher.start(), matcher.group(1).replaceAll("\\s+", " ").trim()));
		}
		return headings;
	}

	private String sectionAt(List<Heading> headings, int offset) {
		String section = "Regulamento";
		for (Heading heading : headings) {
			if (heading.offset() > offset) {
				break;
			}
			section = heading.text();
		}
		return section;
	}

	private int pageAt(List<Integer> pages, int offset) {
		int page = 1;
		for (int index = 0; index < pages.size(); index++) {
			if (pages.get(index) > offset) {
				break;
			}
			page = index + 1;
		}
		return page;
	}

	record Snapshot(Metadata metadata, Map<String, Article> articles, List<String> sections) {
	}

	record Metadata(String title, String resolution, String version, String officialUrl, String sha256, String fileName) {
	}

	record Article(String number, String section, int page, String text) {
	}

	private record Heading(int offset, String text) {
	}
}
