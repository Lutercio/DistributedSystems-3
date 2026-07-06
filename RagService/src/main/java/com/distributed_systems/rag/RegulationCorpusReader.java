package com.distributed_systems.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
class RegulationCorpusReader {

	private static final Pattern ARTICLE = Pattern.compile(
			"(?ms)^\\s*Art\\.\\s*(\\d+)[ºo°]?\\.?\\s*(.*?)(?=^\\s*Art\\.\\s*\\d+|\\z)"
	);
	private static final Pattern SECTION = Pattern.compile(
			"(?m)^\\s*((?:T[IÍ]TULO|CAP[IÍ]TULO|SE[CÇ][AÃ]O)\\s+[^\\r\\n]+)"
	);

	private final RagProperties properties;
	private volatile ParsedCorpus cached;

	RegulationCorpusReader(RagProperties properties) {
		this.properties = properties;
	}

	ParsedCorpus read() {
		ParsedCorpus result = cached;
		if (result == null) {
			synchronized (this) {
				result = cached;
				if (result == null) {
					result = parse();
					cached = result;
				}
			}
		}
		return result;
	}

	List<Document> documents() {
		ParsedCorpus corpus = read();
		List<Document> documents = new ArrayList<>();
		for (CorpusArticle article : corpus.articles()) {
			List<String> chunks = chunks(article.text());
			for (int index = 0; index < chunks.size(); index++) {
				Map<String, Object> metadata = new LinkedHashMap<>();
				metadata.put("documentTitle", corpus.metadata().title());
				metadata.put("resolution", corpus.metadata().resolution());
				metadata.put("article", article.number());
				metadata.put("section", article.section());
				metadata.put("page", article.page());
				metadata.put("officialUrl", corpus.metadata().officialUrl());
				metadata.put("checksum", corpus.metadata().sha256());
				metadata.put("corpusVersion", corpus.metadata().version());
				metadata.put("ingestedAt", Instant.now().toString());
				documents.add(Document.builder()
						.id(corpus.metadata().sha256() + ":" + article.number() + ":" + index)
						.text(chunks.get(index))
						.metadata(metadata)
						.build());
			}
		}
		return documents;
	}

	private ParsedCorpus parse() {
		Path corpusPath = properties.corpusPath();
		Path metadataPath = properties.metadataPath();
		if (!Files.isRegularFile(corpusPath) || !Files.isRegularFile(metadataPath)) {
			throw new IllegalStateException("The versioned regulation corpus is not available");
		}
		CorpusMetadata metadata = readMetadata(metadataPath);
		String actualChecksum = checksum(corpusPath);
		if (!actualChecksum.equalsIgnoreCase(metadata.sha256())) {
			throw new IllegalStateException("Regulation checksum does not match metadata");
		}

		try (PDDocument pdf = Loader.loadPDF(corpusPath.toFile())) {
			PDFTextStripper stripper = new PDFTextStripper();
			StringBuilder text = new StringBuilder();
			List<Integer> pageOffsets = new ArrayList<>();
			for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
				pageOffsets.add(text.length());
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				text.append(stripper.getText(pdf)).append('\n');
			}
			String normalized = text.toString().replace("\u00ad", "").replace('\u00a0', ' ');
			List<Heading> headings = headings(normalized);
			List<CorpusArticle> articles = new ArrayList<>();
			Matcher matcher = ARTICLE.matcher(normalized);
			while (matcher.find()) {
				String body = matcher.group(2).replaceAll("\\s+", " ").trim();
				articles.add(new CorpusArticle(
						matcher.group(1),
						sectionAt(headings, matcher.start()),
						pageAt(pageOffsets, matcher.start()),
						"Art. " + matcher.group(1) + " " + body
				));
			}
			if (articles.isEmpty()) {
				throw new IllegalStateException("No articles were extracted from the regulation");
			}
			return new ParsedCorpus(metadata, List.copyOf(articles), headings.stream().map(Heading::text).distinct().toList());
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to read regulation PDF", exception);
		}
	}

	private List<String> chunks(String value) {
		int size = Math.max(500, properties.chunkCharacters());
		int overlap = Math.clamp(properties.chunkOverlap(), 0, size / 2);
		if (value.length() <= size) {
			return List.of(value);
		}
		List<String> chunks = new ArrayList<>();
		for (int start = 0; start < value.length(); start += size - overlap) {
			int end = Math.min(value.length(), start + size);
			chunks.add(value.substring(start, end));
			if (end == value.length()) {
				break;
			}
		}
		return chunks;
	}

	private CorpusMetadata readMetadata(Path path) {
		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(path)) {
			values.load(input);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to read corpus metadata", exception);
		}
		return new CorpusMetadata(
				required(values, "document.title"),
				required(values, "document.resolution"),
				required(values, "document.version"),
				required(values, "document.official-url"),
				required(values, "document.sha256")
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
			input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (IOException | NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Unable to verify regulation checksum", exception);
		}
	}

	private List<Heading> headings(String text) {
		List<Heading> values = new ArrayList<>();
		Matcher matcher = SECTION.matcher(text);
		while (matcher.find()) {
			values.add(new Heading(matcher.start(), matcher.group(1).replaceAll("\\s+", " ").trim()));
		}
		return values;
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

	private int pageAt(List<Integer> offsets, int offset) {
		int page = 1;
		for (int index = 0; index < offsets.size(); index++) {
			if (offsets.get(index) > offset) {
				break;
			}
			page = index + 1;
		}
		return page;
	}

	record ParsedCorpus(CorpusMetadata metadata, List<CorpusArticle> articles, List<String> sections) {
	}

	private record Heading(int offset, String text) {
	}

}
