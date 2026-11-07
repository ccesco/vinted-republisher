package fr.vinted.republisher.adapter.out.browser;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Invented edit forms in the shape observed on Vinted on 2026-11-07: the page streams its data through scripts
 * {@code self.__next_f.push([1,"…"])} whose decoded texts, put end to end, are rows {@code <key>:<JSON>}.
 */
final class EditFormFixtures {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
        String payload = "0:[\"$\",\"html\",null,{}]\n1:T2a,texte libre\nsur deux lignes\n"
                + String.join("\n", rows) + "\n";
        int middle = payload.length() / 2;
        return List.of("console.log('unrelated')", push(payload.substring(0, middle)), push(payload.substring(middle)));
    }

    static String itemRow(String item) {
        return "5:[\"$\",\"div\",null,{\"children\":[\"$\",\"$L6\",null,{\"item\":" + item + "}]}]";
    }

    private static String push(String chunk) {
        return "self.__next_f.push([1," + JSON.writeValueAsString(chunk) + "])";
    }
}
