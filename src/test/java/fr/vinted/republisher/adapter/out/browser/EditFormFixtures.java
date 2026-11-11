package fr.vinted.republisher.adapter.out.browser;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Invented edit forms in the shape observed on Vinted on 2026-11-07: the page streams its data through scripts
 * {@code self.__next_f.push([1,"…"])} whose decoded texts, put end to end, form React Flight rows: a JSON row
 * {@code <key>:<JSON>} ends with a line break, a text row {@code <key>:T<length in bytes, hex>,<text>} does not.
 */
final class EditFormFixtures {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern TEXT_ROW = Pattern.compile("^[0-9a-f]+:T[0-9a-f]+,", Pattern.DOTALL);

    static final String CATALOG_ROW = """
            7:{"catalogs":[{"id":1904,"title":"Femmes","url":"/catalog/1904-women","catalogs":[\
            {"id":1653,"title":"Chaussures bébé","url":"/catalog/1653-baby-shoes","catalogs":[]}]}]}""";

    private EditFormFixtures() {
    }

    /** A listing like the one observed, with its colour, condition and size. */
    static String anItem(long id) {
        return """
                {"id":%d,"title":"Paire chaussure 9-12 mois","description":"2 paires","colorIds":[12],\
                "catalogId":1653,"packageSizeId":1,"brand":{"id":60,"title":"Kiabi"},"brandId":60,\
                "currency":"EUR","isbn":null,"bookTitle":null,"author":null,"price":1,\
                "measurementWidth":null,"measurementLength":null,"measurementUnit":null,"isAiPhoto":false,\
                "itemAttributes":[{"ids":[2],"code":"condition"},{"ids":[588],"code":"size"}],\
                "isDraft":false,"manufacturer":null,"manufacturerLabel":null,"model":null}""".formatted(id);
    }

    /** The scripts of a form page, the item nested in a component as Next.js does, cut in the middle of a row. */
    static List<String> scriptsWith(String... rows) {
        StringBuilder payload = new StringBuilder("0:[\"$\",\"html\",null,{}]\n")
                .append(textRow("1", "texte libre\nsur deux lignes : 3 ans"));
        for (String row : rows) {
            payload.append(row).append(TEXT_ROW.matcher(row).find() ? "" : "\n");
        }
        int middle = payload.length() / 2;
        return List.of("console.log('unrelated')", push(payload.substring(0, middle)), push(payload.substring(middle)));
    }

    /**
     * The photo gallery of a public listing page, as observed on 2026-11-11, nested in a component: each photo has
     * its full size address, and others the tool does not read.
     */
    static String galleryRow(long id, String... fullSizeUrls) {
        StringBuilder photos = new StringBuilder();
        for (int index = 0; index < fullSizeUrls.length; index++) {
            photos.append(index == 0 ? "" : ",").append("""
                    {"id":%d,"image_no":%d,"width":600,"height":800,"url":"%s/f800","is_main":%b,\
                    "thumbnails":[],"high_resolution":{"id":"05_x","timestamp":1,"orientation":null},\
                    "full_size_url":"%s","is_hidden":false,"extra":{}}""".formatted(
                    1000 + index, index + 1, fullSizeUrls[index], index == 0, fullSizeUrls[index]));
        }
        return """
                c8:["$","$L163",null,{"children":["$","$L165",null,{"data":{"name":"gallery","type":"gallery",\
                "section":"content","data":{"item_id":%d,"seller_id":3,"photos":[%s]}}}]}]""".formatted(id, photos);
    }

    static String itemRow(String item) {
        return "5:[\"$\",\"div\",null,{\"children\":[\"$\",\"$L6\",null,{\"item\":" + item + "}]}]";
    }

    /** React sends a long text apart, in a row the value refers to as {@code "$<key>"}. */
    static String textRow(String key, String text) {
        return key + ":T" + Integer.toHexString(text.getBytes(StandardCharsets.UTF_8).length) + "," + text;
    }

    private static String push(String chunk) {
        return "self.__next_f.push([1," + JSON.writeValueAsString(chunk) + "])";
    }
}
