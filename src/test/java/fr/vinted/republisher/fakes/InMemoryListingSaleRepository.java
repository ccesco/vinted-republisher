package fr.vinted.republisher.fakes;

import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;

import java.util.ArrayList;
import java.util.List;

public class InMemoryListingSaleRepository implements ListingSaleRepository {

    private final List<ListingSale> sales = new ArrayList<>();

    @Override
    public List<ListingSale> findAll() {
        return List.copyOf(sales);
    }

    @Override
    public void addAll(List<ListingSale> added) {
        sales.addAll(added);
    }
}
