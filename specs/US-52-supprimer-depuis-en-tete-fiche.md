# US-52 – « Supprimer de Vinted » en haut de la fiche
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-09 (suppression), US-18b (fiche), US-50 (étape « Vendue »)

## Contexte
Sur la fiche d'une annonce, « Supprimer de Vinted » est au bas de la section Sauvegarde, après la vérification et les photos : il faut défiler pour le trouver. Grisé, il ne dit pas pourquoi. Sur une annonce vendue, il reste actif et le serveur refuse (US-50).

## Objectif
En tant que membre, je veux le bouton « Supprimer de Vinted » en haut de la fiche, avec la raison quand il est grisé, afin de supprimer une annonce sans chercher le bouton ni deviner ce qui manque.

## Comportement attendu
1. **Place** : le bouton « Supprimer de Vinted » passe dans l'en-tête de la fiche, à droite du titre. Il n'est plus dans la section Suppression, qui garde son mode (dry-run), son état, la republication, « Revérifier sur Vinted » et la confirmation.
2. **Action inchangée** : le clic ouvre la même confirmation qu'US-09 (titre, prix, photos, date de validation, « irréversible ») ; « Supprimer définitivement » envoie la même demande (`POST /api/listings/{id}/deletion`). Aucun changement d'API.
3. **Masqué** quand la suppression est confirmée (comme aujourd'hui).
4. **Grisé, avec sa raison** affichée sous le bouton, la première qui s'applique :
   | Situation | Raison |
   | --- | --- |
   | annonce à l'étape « Vendue » | « Annonce vendue : rien à supprimer. » |
   | aucune sauvegarde | « Aucune sauvegarde : sauvegarder d'abord. » |
   | suppression non confirmée | « Suppression non confirmée : revérifier sur Vinted. » |
   | sauvegarde non validée | « Sauvegarde non validée : valider d'abord. » |
   | vérification de la sauvegarde en échec | « Vérification de la sauvegarde en échec. » |
   | une demande est en cours | grisé, sans raison |
5. Actif sinon, sans texte.

## Critères d'acceptation
- [x] « Supprimer de Vinted » est dans l'en-tête de la fiche et plus dans la section Suppression ; la confirmation et la demande envoyée sont celles d'US-09.
- [x] Le bouton est masqué après une suppression confirmée.
- [x] Grisé, il affiche la raison du tableau, dans l'ordre du tableau ; une annonce vendue ou sans sauvegarde n'envoie aucune demande.

## Tests d'acceptation

### AT-52-01 – Supprimer depuis l'en-tête
```gherkin
Étant donné une annonce dont la sauvegarde est validée et vérifiée
Quand le membre ouvre sa fiche
Alors « Supprimer de Vinted » est actif dans l'en-tête, sans raison affichée
Et la section Suppression n'a plus ce bouton
Quand il clique dessus puis sur « Supprimer définitivement »
Alors la demande de suppression d'US-09 est envoyée avec la date de la sauvegarde affichée
```
Test : `ListingDetailPageTest.deletes_from_the_header()`

### AT-52-02 – Raison du bouton grisé
```gherkin
Étant donné une annonce <situation>
Quand le membre ouvre sa fiche
Alors « Supprimer de Vinted » est grisé dans l'en-tête
Et la raison « <raison> » est affichée
Exemples : les lignes du tableau du comportement attendu, point 4
```
Test : `ListingDetailPageTest.header_deletion_shows_why_it_is_disabled()` (logique pure : test Vitest de la fonction qui choisit la raison)

### AT-52-03 – Masqué après suppression confirmée
```gherkin
Étant donné une annonce dont la suppression est confirmée
Quand le membre ouvre sa fiche
Alors l'en-tête n'a pas de bouton « Supprimer de Vinted »
```
Test : `ListingDetailPageTest.header_hides_deletion_once_confirmed()`

## Décisions
- Placement, déplacement et raisons, décidés par l'utilisateur (2026-11-20) : bouton dans l'en-tête à droite du titre, déplacé (pas en double), grisé avec sa raison, y compris sans sauvegarde et pour une annonce vendue ; libellés du point 4 validés avec la spec.

