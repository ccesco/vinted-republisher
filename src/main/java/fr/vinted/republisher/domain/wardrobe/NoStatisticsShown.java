package fr.vinted.republisher.domain.wardrobe;

/** No card of the wardrobe shows its views and favourites: the member is not connected, or Vinted changed its page. */
public class NoStatisticsShown extends RuntimeException {

    public NoStatisticsShown(int cards) {
        super("None of the " + cards + " cards of the wardrobe shows its views and favourites");
    }
}
