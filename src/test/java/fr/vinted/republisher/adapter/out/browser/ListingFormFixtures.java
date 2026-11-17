package fr.vinted.republisher.adapter.out.browser;

/**
 * A local imitation of Vinted's listing form, as observed with the member on 2026-11-15 (AT-12-18): the same
 * identifiers and test ids, options carrying Vinted's identifiers, and Vinted's own suggestions filled in once the
 * photos arrive (another category, another brand, an extra colour, the small package). Publishing posts the form's
 * state to {@code /items/publish}, then goes to the new listing.
 */
final class ListingFormFixtures {

    static final String LISTING_FORM = """
            <!DOCTYPE html><html><head><meta charset="utf-8"><title>Vends ton article | Vinted</title></head><body>
            <h1>Vends ton article</h1>
            <div id="photos" data-testid="media-upload">
              <input type="file" name="photos" data-testid="add-photos-input" multiple
                     accept="image/jpeg,image/gif,image/png,image/webp" style="display:none">
              <div data-testid="media-upload-grid"></div>
            </div>
            <input type="text" id="title" name="title" data-testid="title--input">
            <textarea id="description" name="description" data-testid="description--input"></textarea>
            <input id="category" name="category" data-testid="catalog-select-dropdown-input" readonly>
            <div id="category-panel" data-testid="catalog-select-dropdown-content" hidden>
              <input id="catalog-search-input" data-testid="catalog-search-input"><div id="catalog-results"></div>
            </div>
            <div id="details" hidden>%s</div>
            <input type="text" id="price" name="price" data-testid="price-input--input" placeholder="0,00 €">
            <div id="packages" hidden>
              <div id="package-size-1" data-testid="1-package-size--cell">Petit
                <input type="radio" name="package" id="package_type_selector_1"
                       data-testid="package_type_selector_1--input" checked></div>
              <div id="package-size-2" data-testid="2-package-size--cell">Moyen
                <input type="radio" name="package" id="package_type_selector_2"
                       data-testid="package_type_selector_2--input"></div>
              <div id="package-size-3" data-testid="3-package-size--cell">Grand
                <input type="radio" name="package" id="package_type_selector_3"
                       data-testid="package_type_selector_3--input"></div>
            </div>
            <div id="boost" hidden><label>Vends plus vite avec un Boost
              <input type="checkbox" data-testid="boost-checkbox"></label></div>
            <button type="button" data-testid="upload-form-save-draft-button">Sauvegarder le brouillon</button>
            %s
            <script>
              addEventListener('click', (event) => {
                const control = event.target.closest('[data-testid], [id]');
                fetch('/interaction?click=' + (control ? (control.dataset.testid || control.id) : 'page'));
              }, true);

              const catalog = [[1596, 'Pyjamas une pièce', 'Enfants > Vêtements pour filles'],
                [1754, 'Pyjamas une pièce', 'Enfants > Vêtements pour garçons'],
                [1597, 'Pyjamas deux pièces', 'Enfants > Vêtements pour filles']];
              const brands = [[149, 'Petit Bateau'], [60, 'Kiabi'], [1250, 'Petit Béguin']];
              const state = {category: null, brand: null, size: null, condition: null, colors: [], materials: [],
                photos: []};
              const $ = (selector) => document.querySelector(selector);
              const toggle = (opener, panel) => $(opener).addEventListener('click', () => $(panel).hidden = !$(panel).hidden);

              function chooseCategory(id) {
                state.category = id;
                $('#category').value = catalog.find(entry => entry[0] === id)[1];
                $('#category-panel').hidden = true;
                $('#details').hidden = false;
                $('#packages').hidden = false;
              }
              function chooseBrand(id) {
                state.brand = id;
                $('#brand').value = id === 0 ? 'Sans marque' : brands.find(entry => entry[0] === id)[1];
                $('#brand-panel').hidden = true;
                // A suggestion arriving once the member has chosen, over their choice.
                if (%s && id === 149) setTimeout(() => chooseBrand(60), 200);
              }
              function single(selector, field, input) {
                document.querySelectorAll(selector).forEach(option => option.addEventListener('click', () => {
                  state[field] = Number(option.dataset.option);
                  document.querySelectorAll(selector).forEach(other =>
                    other.setAttribute('aria-checked', String(other === option)));
                  $(input).value = option.innerText;
                  option.closest('[hidden], .panel').hidden = true;
                }));
              }
              function tick(field, id, input, maximum) {
                const ticked = state[field].includes(id);
                if (!ticked && state[field].length >= maximum) return;
                state[field] = ticked ? state[field].filter(other => other !== id) : [...state[field], id];
                document.querySelectorAll('[data-option-of=' + field + ']').forEach(option =>
                  option.setAttribute('aria-checked', String(state[field].includes(Number(option.dataset.option)))));
                $(input).value = state[field].join(', ');
              }
              function multiple(field, input, maximum) {
                document.querySelectorAll('[data-option-of=' + field + ']').forEach(option =>
                  option.addEventListener('click', () => tick(field, Number(option.dataset.option), input, maximum)));
              }

              addEventListener('load', () => {
                toggle('#category', '#category-panel');
                $('#catalog-search-input').addEventListener('input', (event) => {
                  const query = event.target.value.toLowerCase();
                  $('#catalog-results').innerHTML = catalog.filter(entry => entry[1].toLowerCase().includes(query))
                    .map(entry => `<div id="catalog-search-${entry[0]}-result" role="radio">${entry[1]} ${entry[2]}</div>`)
                    .join('');
                  document.querySelectorAll('[id^=catalog-search-][id$=-result]').forEach(result =>
                    result.addEventListener('click', () => chooseCategory(Number(result.id.split('-')[2]))));
                });
                if ($('#brand')) {
                  toggle('#brand', '#brand-panel');
                  $('#brand-search-input').addEventListener('input', (event) => {
                    const query = event.target.value.toLowerCase();
                    $('#brand-results').innerHTML = brands.filter(entry => entry[1].toLowerCase().includes(query))
                      .map(entry => `<div id="brand-${entry[0]}" role="radio">${entry[1]}</div>`).join('');
                    document.querySelectorAll('#brand-results [role=radio]').forEach(result =>
                      result.addEventListener('click', () => chooseBrand(Number(result.id.split('-')[1]))));
                  });
                  $('[data-testid=empty-brand]').addEventListener('click', () => chooseBrand(0));
                }
                for (const field of ['size', 'condition', 'color', 'material']) {
                  if ($('#' + field)) toggle('#' + field, '#' + field + '-panel');
                }
                single('[data-testid^=size-group-]', 'size', '#size');
                single('[data-testid^=condition-]', 'condition', '#condition');
                multiple('colors', '#color', 2);
                multiple('materials', '#material', 3);
                document.querySelectorAll('[id^=package-size-]').forEach(cell => cell.addEventListener('click', () =>
                  cell.querySelector('input').checked = true));
                $('#price').addEventListener('input', () => $('#boost').hidden = false);

                $('[data-testid=add-photos-input]').addEventListener('change', (event) => {
                  state.photos = [...event.target.files].map(file => file.name + ':' + file.size);
                  // As observed: a progress bar while the photos are sent to Vinted.
                  const progress = document.createElement('div');
                  progress.setAttribute('role', 'progressbar');
                  $('[data-testid=media-upload]').append(progress);
                  setTimeout(() => progress.remove(), %s);
                  setTimeout(() => {
                    $('[data-testid=media-upload-grid]').innerHTML = state.photos
                      .map((photo, rank) => `<div data-testid="media-select-photo-${rank}">${photo}</div>`).join('');
                    // Vinted's own suggestions, which the saved values must win over.
                    chooseCategory(1754);
                    if ($('#brand')) chooseBrand(60);
                    if ($('#color')) tick('colors', 1, '#color', 2);
                  }, 300);
                });

                const publish = $('[data-testid=upload-form-save-button]');
                if (publish) publish.addEventListener('click', () => {
                  if (document.querySelector('[role=progressbar]')) {
                    // A refusal shown on the form itself, which stays.
                    const refusal = document.createElement('div');
                    refusal.setAttribute('role', 'alert');
                    refusal.textContent = 'Attends la fin du téléchargement des photos';
                    document.body.append(refusal);
                    return;
                  }
                  // A third party, like the anti-bot or analytics scripts of the real page, posting at the same time.
                  fetch('http://localhost:' + location.port + '/telemetry',
                    {method: 'POST', mode: 'no-cors', body: 'sent'}).catch(() => {});
                  const form = {photos: state.photos, title: $('#title').value,
                    description: $('#description').value, price: $('#price').value, category: state.category,
                    brand: state.brand, size: state.size, condition: state.condition,
                    colors: [...state.colors].sort((a, b) => a - b),
                    materials: [...state.materials].sort((a, b) => a - b),
                    packageSize: Number(document.querySelector('[name=package]:checked').id.split('_')[3]),
                    boost: $('[data-testid=boost-checkbox]').checked};
                  fetch('/items/publish', {method: 'POST', body: JSON.stringify(form)})
                    .then(response => response.ok ? response.text() : '').then(next => {
                      if (next) {
                        location.href = next;
                        return;
                      }
                      const refusal = document.createElement('span');
                      refusal.setAttribute('data-testid', 'price-input--validation');
                      refusal.textContent = 'Le prix minimum est de 1,00 €';
                      document.body.append(refusal);
                    });
                });
              });
            </script>
            </body></html>""";

    static final String BRAND = """
            <input id="brand" name="brand" data-testid="brand-select-dropdown-input" readonly>
            <div id="brand-panel" data-testid="brand-select-dropdown-content" hidden>
              <input id="brand-search-input" data-testid="brand-search--input"><div id="brand-results"></div>
              <div data-testid="empty-brand" role="radio">Sans marque</div>
            </div>""";

    static final String SIZE = """
            <input id="size" name="size" data-testid="category-size-single-grid-input" readonly>
            <div id="size-panel" class="panel" data-testid="category-size-single-grid-content" hidden>
              <div data-testid="size-group-32-grid-option-613" data-option="613" role="checkbox"
                   aria-checked="false">1-3 mois / 56 cm</div>
              <div data-testid="size-group-32-grid-option-614" data-option="614" role="checkbox"
                   aria-checked="false">3-6 mois / 62 cm</div>
            </div>""";

    static final String CONDITION = """
            <input id="condition" name="condition" data-testid="category-condition-single-list-input" readonly>
            <div id="condition-panel" class="panel" hidden>
              <div id="condition-6" data-testid="condition-6" data-option="6" role="radio">Neuf avec étiquette</div>
              <div id="condition-2" data-testid="condition-2" data-option="2" role="radio">Très bon état</div>
            </div>""";

    /** Multicolore shows twice, as a suggestion and in the full list, as observed. */
    static final String COLOR = """
            <input id="color" name="color" data-testid="color-select-dropdown-input" readonly>
            <div id="color-panel" hidden>
              <div data-testid="filter-grid-option-15" data-option="15" data-option-of="colors" role="checkbox"
                   aria-checked="false">Multicolore</div>
              <div data-testid="filter-grid-option-1" data-option="1" data-option-of="colors" role="checkbox"
                   aria-checked="false">Noir</div>
              <div data-testid="filter-grid-option-12" data-option="12" data-option-of="colors" role="checkbox"
                   aria-checked="false">Blanc</div>
              <div data-testid="filter-grid-option-15" data-option="15" data-option-of="colors" role="checkbox"
                   aria-checked="false">Multicolore</div>
            </div>""";

    static final String MATERIAL = """
            <input id="material" name="material" data-testid="category-material-multi-list-input" readonly>
            <div id="material-panel" hidden>
              <div id="material-suggestion-44" data-testid="material-suggestion-44" data-option="44"
                   data-option-of="materials" role="checkbox" aria-checked="false">Coton</div>
              <div id="material-468" data-testid="material-468" data-option="468" data-option-of="materials"
                   role="checkbox" aria-checked="false">Acier</div>
              <div id="material-44" data-testid="material-44" data-option="44" data-option-of="materials"
                   role="checkbox" aria-checked="false">Coton</div>
            </div>""";

    static final String ALL_DETAILS = BRAND + SIZE + CONDITION + COLOR + MATERIAL;

    static final String PUBLISH_BUTTON =
            "<button type=\"button\" data-testid=\"upload-form-save-button\">Ajouter</button>";

    private ListingFormFixtures() {
    }
}
