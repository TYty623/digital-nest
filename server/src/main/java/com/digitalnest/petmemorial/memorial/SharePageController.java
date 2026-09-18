package com.digitalnest.petmemorial.memorial;

import com.digitalnest.petmemorial.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SharePageController {

    private static final String GENERIC_TITLE = "数字小窝 | 宠物数字纪念";
    private static final String GENERIC_DESCRIPTION = "为 TA 留下一处私密、温柔、可以随时回来看看的数字小窝。";

    private final MemorialService memorialService;

    public SharePageController(MemorialService memorialService) {
        this.memorialService = memorialService;
    }

    @GetMapping(value = "/m/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String sharePage(@PathVariable String slug, HttpServletRequest request) {
        ShareMetadata metadata = metadataFor(slug, request);
        String imageTag = metadata.imageUrl() == null ? "" : "\n    <meta property=\"og:image\" content=\"" + metadata.imageUrl() + "\">";
        return """
                <!doctype html>
                <html lang="zh-CN">
                  <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <meta name="robots" content="%s">
                    <meta name="description" content="%s">
                    <meta property="og:type" content="website">
                    <meta property="og:title" content="%s">
                    <meta property="og:description" content="%s">
                    <meta property="og:url" content="%s">%s
                    <title>%s</title>
                    <link rel="stylesheet" href="/assets/app.css">
                  </head>
                  <body>
                    <div id="app"></div>
                    <script type="module" src="/assets/app.js"></script>
                  </body>
                </html>
                """.formatted(
                metadata.robots(), metadata.description(), metadata.title(), metadata.description(), metadata.url(), imageTag,
                metadata.title());
    }

    private ShareMetadata metadataFor(String slug, HttpServletRequest request) {
        try {
            Memorial memorial = memorialService.findPublished(slug);
            if ("PASSWORD".equals(memorial.visibility())) {
                return genericMetadata(request);
            }
            String title = "纪念" + memorial.petName() + " | 数字小窝";
            String description = memorial.farewellMessage() == null || memorial.farewellMessage().isBlank()
                    ? GENERIC_DESCRIPTION
                    : memorial.farewellMessage();
            String imageUrl = memorial.coverMediaId() == null ? null
                    : origin(request) + "/api/v1/media/" + memorial.coverMediaId() + "/content";
            String robots = "PUBLIC".equals(memorial.visibility()) ? "index,follow" : "noindex,nofollow";
            return new ShareMetadata(escape(title), escape(description), robots, escape(request.getRequestURL().toString()),
                    imageUrl == null ? null : escape(imageUrl));
        } catch (ApiException exception) {
            return genericMetadata(request);
        }
    }

    private ShareMetadata genericMetadata(HttpServletRequest request) {
        return new ShareMetadata(GENERIC_TITLE, GENERIC_DESCRIPTION, "noindex,nofollow",
                escape(request.getRequestURL().toString()), null);
    }

    private String origin(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record ShareMetadata(String title, String description, String robots, String url, String imageUrl) {
    }
}
