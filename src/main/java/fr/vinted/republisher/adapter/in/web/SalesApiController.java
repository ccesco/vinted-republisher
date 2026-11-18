package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.SalesApi;
import fr.vinted.republisher.adapter.in.web.api.model.PriceDto;
import fr.vinted.republisher.adapter.in.web.api.model.SaleDto;
import fr.vinted.republisher.adapter.in.web.api.model.SalesOverviewDto;
import fr.vinted.republisher.application.sale.ShowSales;
import fr.vinted.republisher.domain.sale.Sale;
import fr.vinted.republisher.domain.sale.SalesOverview;
import fr.vinted.republisher.domain.wardrobe.Price;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

@RestController
public class SalesApiController implements SalesApi {

    private final ShowSales showSales;

    public SalesApiController(ShowSales showSales) {
        this.showSales = showSales;
    }

    @Override
    public ResponseEntity<SalesOverviewDto> showSales() {
        SalesOverview overview = showSales.show();
        return ResponseEntity.ok(new SalesOverviewDto()
                .sales(overview.sales().stream().map(SalesApiController::toDto).toList())
                .totals(overview.totals().stream().map(SalesApiController::toDto).toList()));
    }

    private static SaleDto toDto(Sale sale) {
        return new SaleDto()
                .listingId(sale.listingId().value())
                .title(sale.title())
                .price(toDto(sale.price()))
                .noticedAt(sale.noticedAt().atOffset(ZoneOffset.UTC));
    }

    private static PriceDto toDto(Price price) {
        return new PriceDto().amount(price.amount()).currency(price.currency());
    }
}
