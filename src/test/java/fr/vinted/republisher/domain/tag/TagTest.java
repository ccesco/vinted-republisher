package fr.vinted.republisher.domain.tag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagTest {

    @Test
    void is_kept_trimmed_with_single_inner_spaces_and_in_lower_case_with_its_accents() {
        assertThat(new Tag("  Été \t  À  Baisser ").name()).isEqualTo("été à baisser");
    }

    @Test
    void two_entries_of_the_same_form_are_the_same_tag() {
        assertThat(new Tag(" LOT ")).isEqualTo(new Tag("lot"));
    }

    @Test
    void decomposed_accents_make_the_same_tag() {
        assertThat(new Tag("été")).isEqualTo(new Tag("été"));
    }

    @Test
    void rejects_blank_and_too_long_tags() {
        // Quand le membre saisit un tag fait d'espaces, ou de 31 caractères une fois mis en forme
        // Alors le tag est refusé
        assertThatThrownBy(() -> new Tag("   ")).isInstanceOf(InvalidTag.class);
        assertThatThrownBy(() -> new Tag(null)).isInstanceOf(InvalidTag.class);
        assertThatThrownBy(() -> new Tag("a".repeat(31))).isInstanceOf(InvalidTag.class)
                .hasMessage("Tag longer than 30 characters");
    }

    @Test
    void accepts_30_characters_once_formed() {
        assertThat(new Tag("  " + "a".repeat(30) + "  ").name()).hasSize(30);
    }

    @Test
    void sorts_in_french_alphabetical_order() {
        List<Tag> sorted = Stream.of("zèbre", "lot", "été", "Ecru").map(Tag::new).sorted().toList();

        assertThat(sorted).extracting(Tag::name).containsExactly("ecru", "été", "lot", "zèbre");
    }
}
