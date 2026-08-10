package cn.mythicland.mythicmobsaddon.web;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.web.*;
import cn.mythicland.mythicmobsaddon.api.*;
import cn.mythicland.mythicmobsaddon.service.MythicItemException;
import cn.mythicland.mythicmobsaddon.service.MythicItemService;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Business routes for the MythicMobsAddon web console. HTTP parsing and authentication are owned
 * by Lib; this class only converts requests to MM domain operations.
 */
public final class MythicMobsAddonWebHandler extends AuthenticatedHttpHandler {

    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;
    private static final int MAX_IMPORT_FILES = 50;
    private static final int MAX_IMPORT_FILE_BYTES = 4 * 1024 * 1024;

    private final JavaPlugin plugin;
    private final LibApi lib;
    private final MythicItemService service;

    public MythicMobsAddonWebHandler(JavaPlugin plugin, LibApi lib, MythicItemService service, String token) {
        super(plugin.getLogger(), WebAuth.token(token, "X-MythicMobsAddon-Token"), MAX_BODY_BYTES);
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.service = Objects.requireNonNull(service, "service");
    }

    private static String string(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static boolean confirmed(Map<String, Object> values) {
        Object value = values.get("confirmExternalMutation");
        if (value == null) return false;
        if (value instanceof Boolean booleanValue) return booleanValue;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> configuration(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new WebException(400, "INVALID_FIELD", "configuration 必须是对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    private static int integer(String raw, int defaultValue, int minimum, int maximum) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            int value = Integer.parseInt(raw);
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new WebException(400, "INVALID_FIELD", "分页参数无效");
        }
    }

    private static String displayFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String result = (separator < 0 ? normalized : normalized.substring(separator + 1)).trim();
        if (result.isBlank()) throw new WebException(400, "IMPORT_FILE_NAME", "导入文件名不能为空");
        return result;
    }

    private static boolean isYamlFileName(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".yml") || normalized.endsWith(".yaml");
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new WebException(400, "INVALID_FIELD", "枚举参数无效: " + raw);
        }
    }

    @Override
    protected boolean requiresAuthentication(WebRequest request) {
        return request.path().startsWith("/api/");
    }

    @Override
    protected void handleAuthenticated(WebRequest request, WebResponse response) throws IOException {
        try {
            if (!request.path().startsWith("/api/")) {
                serveAsset(request.path(), response);
                return;
            }
            handleApi(request, response);
        } catch (MythicItemException exception) {
            throw domainError(exception);
        }
    }

    private void handleApi(WebRequest request, WebResponse response) throws IOException {
        String method = request.method().toUpperCase(Locale.ROOT);
        String path = request.path();
        if (method.equals("GET") && path.equals("/api/status")) {
            response.json(200, onMain(this::status));
            return;
        }
        if (method.equals("GET") && path.equals("/api/items")) {
            response.json(200, page(request));
            return;
        }
        if (method.equals("GET") && path.equals("/api/editor/catalog")) {
            response.json(200, editorCatalog(onMain(service::editorCatalog)));
            return;
        }
        if (method.equals("GET") && path.equals("/api/taxonomy")) {
            response.json(200, taxonomy(onMain(service::taxonomy)));
            return;
        }
        if (method.equals("GET") && path.startsWith("/api/items/") && path.length() > "/api/items/".length()) {
            String internalName = path.substring("/api/items/".length());
            MythicItemDetails details = onMain(() -> service.find(internalName)
                    .orElseThrow(() -> new MythicItemException("ITEM_NOT_FOUND", "MM 物品不存在: " + internalName)));
            response.json(200, details(details));
            return;
        }
        if (method.equals("POST") && path.equals("/api/items")) {
            MythicItemCreateRequest create = createRequest(request.readJson());
            write(response, onMain(() -> service.create(create)));
            return;
        }
        if (method.equals("POST") && path.equals("/api/import/preview")) {
            MythicItemImportRequest importRequest = importRequest(request);
            response.json(200, importResult(onMain(() -> service.previewImport(importRequest))));
            return;
        }
        if (method.equals("POST") && path.equals("/api/import")) {
            MythicItemImportRequest importRequest = importRequest(request);
            response.json(200, importResult(onMain(() -> service.importItems(importRequest))));
            return;
        }
        if (method.equals("PUT") && path.startsWith("/api/items/") && path.length() > "/api/items/".length()) {
            String internalName = path.substring("/api/items/".length());
            Map<String, Object> body = request.readJson();
            MythicItemUpdateRequest update = new MythicItemUpdateRequest(
                    internalName,
                    string(body, "newInternalName", internalName),
                    configuration(body.get("configuration")),
                    classification(body.get("classification")),
                    string(body, "expectedRevision", ""),
                    confirmed(body)
            );
            write(response, onMain(() -> service.update(update)));
            return;
        }
        if (method.equals("DELETE") && path.startsWith("/api/items/") && path.length() > "/api/items/".length()) {
            String internalName = path.substring("/api/items/".length());
            Map<String, Object> body = request.body().length == 0 ? Map.of() : request.readJson();
            write(response, onMain(() -> service.delete(new MythicItemDeleteRequest(
                    internalName,
                    string(body, "expectedRevision", ""),
                    confirmed(body)
            ))));
            return;
        }
        if (method.equals("POST") && path.equals("/api/taxonomy/tags")) {
            Map<String, Object> body = request.readJson();
            response.json(200, taxonomy(onMain(() -> service.createTag(
                    string(body, "displayName", ""),
                    string(body, "color", "")
            ))));
            return;
        }
        if (method.equals("PUT") && path.startsWith("/api/taxonomy/tags/")
                && path.length() > "/api/taxonomy/tags/".length()) {
            Map<String, Object> body = request.readJson();
            response.json(200, taxonomy(onMain(() -> service.saveTag(
                    path.substring("/api/taxonomy/tags/".length()),
                    string(body, "displayName", ""),
                    string(body, "color", "")
            ))));
            return;
        }
        if (method.equals("DELETE") && path.startsWith("/api/taxonomy/tags/")
                && path.length() > "/api/taxonomy/tags/".length()) {
            response.json(200, taxonomy(onMain(() -> service.deleteTag(
                    path.substring("/api/taxonomy/tags/".length())
            ))));
            return;
        }
        if (method.equals("POST") && path.equals("/api/reload")) {
            response.json(200, reload(onMain(service::reload)));
            return;
        }
        response.notFound("接口不存在");
    }

    private Map<String, Object> page(WebRequest request) {
        String sourceValue = request.query("source");
        String statusValue = request.query("status");
        String sortValue = request.query("sort");
        MythicItemSource source = enumValue(MythicItemSource.class, sourceValue, MythicItemSource.ALL);
        MythicItemStatus status = statusValue == null || statusValue.isBlank()
                ? null : enumValue(MythicItemStatus.class, statusValue, null);
        MythicItemSort sort = enumValue(MythicItemSort.class, sortValue, MythicItemSort.TAG_THEN_MATERIAL);
        MythicItemQuery query = new MythicItemQuery(
                request.query("search"), source, status,
                integer(request.query("page"), 0, 0, Integer.MAX_VALUE),
                integer(request.query("pageSize"), 50, 1, 200),
                sort,
                request.query("tag")
        );
        return page(onMain(() -> service.search(query)));
    }

    private Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("plugin", "MythicMobsAddon");
        status.put("version", plugin.getDescription().getVersion());
        Plugin mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs");
        status.put("mythicMobsVersion", mythicMobs == null ? "" : mythicMobs.getDescription().getVersion());
        status.put("managedFile", service.managedFile().toString().replace('\\', '/'));
        MythicItemPage page = onMain(
                () -> service.search(MythicItemQuery.defaults())
        );
        status.put("generation", page.generation());
        status.put("itemCount", page.total());
        return status;
    }

    private void write(WebResponse response, MythicItemWriteResult result) throws IOException {
        int status = switch (result.status()) {
            case CREATED -> 201;
            case CONFLICT -> 409;
            case INVALID -> 400;
            case FAILED -> 500;
            case UPDATED, RENAMED, DELETED -> 200;
        };
        response.json(status, writeResult(result));
    }

    private Map<String, Object> writeResult(MythicItemWriteResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.status().name());
        response.put("internalName", result.internalName());
        response.put("message", result.message());
        response.put("revision", result.revision());
        response.put("previousInternalName", result.previousInternalName());
        response.put("affectedFiles", result.affectedFiles());
        if (result.item() != null) response.put("item", details(result.item()));
        return response;
    }

    private MythicItemImportRequest importRequest(WebRequest request) {
        if (!request.isMultipart()) {
            throw new WebException(400, "INVALID_MULTIPART", "导入请求必须使用 multipart/form-data");
        }
        MultipartParser.MultipartData multipart = request.readMultipart();
        List<MultipartParser.Part> parts = multipart.named("files");
        if (parts.isEmpty()) throw new WebException(400, "IMPORT_EMPTY", "至少选择一个 YAML 文件");
        if (parts.size() > MAX_IMPORT_FILES) {
            throw new WebException(400, "IMPORT_TOO_MANY_FILES", "单次最多导入 " + MAX_IMPORT_FILES + " 个 YAML 文件");
        }
        List<MythicItemImportFile> files = new ArrayList<>();
        for (MultipartParser.Part part : parts) {
            String fileName = displayFileName(part.fileName());
            if (!isYamlFileName(fileName)) {
                throw new WebException(400, "IMPORT_FILE_TYPE", fileName + " 不是 YAML 文件");
            }
            byte[] content = part.content();
            if (content.length > MAX_IMPORT_FILE_BYTES) {
                throw new WebException(413, "IMPORT_FILE_TOO_LARGE", fileName + " 不能超过 4 MB");
            }
            files.add(new MythicItemImportFile(fileName, content));
        }
        return new MythicItemImportRequest(files);
    }

    private Map<String, Object> importResult(MythicItemImportResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", result.status().name());
        value.put("message", result.message());
        value.put("fileCount", result.fileCount());
        value.put("candidates", result.candidates().stream().map(this::importCandidate).toList());
        value.put("conflicts", result.conflicts());
        value.put("warnings", result.warnings());
        value.put("errors", result.errors());
        return value;
    }

    private Map<String, Object> importCandidate(MythicItemImportCandidate candidate) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("internalName", candidate.internalName());
        value.put("fileName", candidate.fileName());
        value.put("format", candidate.format());
        return value;
    }

    private Map<String, Object> page(MythicItemPage page) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", page.items().stream().map(this::summary).toList());
        response.put("page", page.page());
        response.put("pageSize", page.pageSize());
        response.put("total", page.total());
        response.put("generation", page.generation());
        return response;
    }

    private Map<String, Object> summary(MythicItemSummary summary) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("internalName", summary.internalName());
        value.put("relativeFile", summary.relativeFile());
        value.put("managed", summary.managed());
        value.put("status", summary.status().name());
        value.put("displayName", summary.displayName());
        value.put("materialName", summary.materialName());
        value.put("amount", summary.amount());
        value.put("warnings", summary.warnings());
        value.put("revision", summary.revision());
        value.put("editable", summary.editable());
        value.put("classification", classification(summary.classification()));
        value.put("iconUrls", summary.iconUrls());
        return value;
    }

    private Map<String, Object> details(MythicItemDetails details) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("summary", summary(details.summary()));
        value.put("configuration", details.configuration());
        value.put("rawYaml", details.rawYaml());
        value.put("revision", details.revision());
        value.put("preview", preview(details.preview()));
        value.put("classification", classification(details.summary().classification()));
        return value;
    }

    private Map<String, Object> preview(ItemStack stack) {
        if (stack == null) return Map.of();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("material", stack.getType().name());
        value.put("amount", stack.getAmount());
        short durability = stack.getDurability();
        value.put("durability", durability);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            value.put("displayName", meta.getDisplayName());
            value.put("lore", meta.getLore() == null ? List.of() : meta.getLore());
            value.put("enchantments", meta.getEnchants().entrySet().stream().collect(
                    Collectors.toMap(
                            entry -> entry.getKey().getName(),
                            Map.Entry::getValue,
                            (left, right) -> right,
                            LinkedHashMap::new
                    )
            ));
            value.put("flags", meta.getItemFlags().stream().map(Enum::name).toList());
            value.put("unbreakable", meta.isUnbreakable());
        }
        return value;
    }

    private Map<String, Object> reload(MythicItemsReloadResult result) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("success", result.success());
        value.put("itemCount", result.itemCount());
        value.put("generation", result.generation());
        value.put("message", result.message());
        return value;
    }

    private MythicItemCreateRequest createRequest(Map<String, Object> body) {
        return new MythicItemCreateRequest(
                string(body, "internalName", ""),
                configuration(body.get("configuration")),
                classification(body.get("classification"))
        );
    }

    private Map<String, Object> editorCatalog(MythicItemEditorCatalog catalog) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materials", catalog.materials());
        result.put("enchantments", catalog.enchantments());
        result.put("itemFlags", catalog.itemFlags());
        result.put("taxonomy", taxonomy(catalog.taxonomy()));
        return result;
    }

    private Map<String, Object> taxonomy(MythicItemTaxonomy taxonomy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tags", taxonomy.tags());
        return result;
    }

    private Map<String, Object> classification(MythicItemClassification classification) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tagIds", classification.tagIds());
        return result;
    }

    private MythicItemClassification classification(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return MythicItemClassification.defaults();
        Map<String, Object> values = new LinkedHashMap<>();
        raw.forEach((key, child) -> values.put(String.valueOf(key), child));
        Object tags = values.get("tagIds");
        List<String> tagIds = tags instanceof Iterable<?> iterable
                ? StreamSupport.stream(iterable.spliterator(), false).map(String::valueOf).toList()
                : List.of();
        return new MythicItemClassification(tagIds);
    }

    private <T> T onMain(Supplier<T> supplier) {
        try {
            if (Bukkit.isPrimaryThread()) return supplier.get();
            return lib.supplyOnMain(supplier).get(20, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WebException(503, "MAIN_THREAD_INTERRUPTED", "主线程处理被中断");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof MythicItemException domain) throw domainError(domain);
            if (cause instanceof WebException web) throw web;
            throw new WebException(500, "DOMAIN_FAILURE", cause.getMessage() == null ? "业务处理失败" : cause.getMessage());
        } catch (TimeoutException exception) {
            throw new WebException(503, "MAIN_THREAD_TIMEOUT", "主线程处理超时");
        }
    }

    private WebException domainError(MythicItemException exception) {
        int status = switch (exception.code()) {
            case "ITEM_NOT_FOUND" -> 404;
            case "CONFLICT", "REFERENCE_CHECK_FAILED" -> 409;
            default -> 400;
        };
        return new WebException(
                status,
                exception.code(),
                exception.getMessage() == null ? "请求失败" : exception.getMessage()
        );
    }

    private void serveAsset(String path, WebResponse response) throws IOException {
        String assetPath = path;
        if (assetPath.equals("/web") || assetPath.equals("/web/")) {
            assetPath = "/";
        } else if (assetPath.startsWith("/web/")) {
            assetPath = assetPath.substring("/web".length());
        }
        String resource = assetPath.equals("/") || assetPath.isBlank() ? "web/index.html" : "web" + assetPath;
        if (resource.contains("..") || resource.contains("\\") || !resource.startsWith("web/")) {
            response.notFound("网页资源不存在");
            return;
        }
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) {
                response.notFound("网页资源不存在");
                return;
            }
            String contentType = resource.endsWith(".css") ? "text/css; charset=utf-8"
                    : resource.endsWith(".js") ? "application/javascript; charset=utf-8"
                      : "text/html; charset=utf-8";
            response.send(200, contentType, input.readAllBytes());
        } catch (IOException exception) {
            throw new WebException(500, "ASSET_READ_FAILED", "网页资源读取失败");
        }
    }
}
