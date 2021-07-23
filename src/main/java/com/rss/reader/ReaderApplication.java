package com.rss.reader;

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URL;
import java.util.*;

@SpringBootApplication
public class ReaderApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(ReaderApplication.class, args);
	}

	@Autowired
	private SyndFeed externalRssFeed;

	@Autowired
	private ContentParserFactory contentParserFactory;

	@Bean
	SyndFeed externalRssFeed(
		@Value("${external.rss.feed.url}") String externalFeedUrl) throws IOException, FeedException {
		final URL feedSource = new URL(externalFeedUrl);
		final SyndFeedInput input = new SyndFeedInput();
		return input.build(new XmlReader(feedSource));
	}

	@Bean
	ContentParserFactory contentParserFactory() {
		return new ContentParserFactory();
	}

	@Override
	public void run(String... args) throws Exception {
		final String feedDescription = this.externalRssFeed.getDescription();
		System.out.printf("Reading RSS feed at [%s]...\n\n", feedDescription);

		final List<SyndEntry> entries = (List<SyndEntry>) this.externalRssFeed.getEntries();

		for (SyndEntry entry: entries) {
			System.out.println();

			final StringBuilder descriptionValue = new StringBuilder();
			final SyndContent description = entry.getDescription();
			final Optional<ContentParser> contentParserOptional = contentParserFactory.getParser(description.getType());

			if (contentParserOptional.isPresent()) {
				final ContentParser parser = contentParserOptional.get();
				final Content content = parser.parse(description.getValue());
				final String[] contentValues = content.getValues();
				for (String contentValue : contentValues) {
					if (descriptionValue.length() > 0) {
						descriptionValue.append("\n");
					}
					descriptionValue.append(contentValue);
				}
				if (descriptionValue.length() > 0) {
					descriptionValue.append("\n\n");
				}
			}

			System.out.printf("Entry [%s - %s] \n%s", entry.getTitle(), entry.getLink(), descriptionValue.toString());
		}

	}

	interface Content {
		String getValue();
		String[] getValues();
	}

	interface ContentParser {
		Content parse(String contextAsString);
	}

	static class ContentParserFactory {

		private static final Map<String, ContentParser> contentParserMap;

		static {
			contentParserMap = new HashMap<>();
			contentParserMap.put("text/html", new HtmlContentParser());
		}

		public Optional<ContentParser> getParser(String type) {
			return Optional.of(contentParserMap.getOrDefault(type, null));
		}
	}

	static class HtmlContentParser implements ContentParser {

		@Override
		public Content parse(String contextAsString) {
			return new HtmlContent(Jsoup.parse(contextAsString));
		}

		private record HtmlContent(Document htmlDocument) implements Content {

			@Override
			public String[] getValues() {
				final List<String> values = new ArrayList<>();
				final Elements p = this.htmlDocument.select("p");
				for (Element element : p) {
					values.add(element.text().trim());
				}
				return values.toArray(new String[0]);
			}

			@Override
			public String getValue() {
				return this.htmlDocument.select("p").first().text().trim();
			}
		}
	}
}
