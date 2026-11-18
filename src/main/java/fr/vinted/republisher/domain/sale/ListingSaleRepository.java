package fr.vinted.republisher.domain.sale;

import java.util.List;

public interface ListingSaleRepository {

    List<ListingSale> findAll();

    /** All or nothing. */
    void addAll(List<ListingSale> sales);
}
