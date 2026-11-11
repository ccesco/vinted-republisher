# Recette manuelle – Épic 2 « Scan et sauvegarde » (US-04 à US-08)

Une seule passe sur le vrai compte, dans l'ordre, qui couvre les tests manuels encore ouverts :
AT-04-16 (déjà passé le 2026-11-07, refait ici pour la reprise), AT-05-17, AT-06-12, AT-07-10 et AT-08-15.

**Rien n'est modifié sur Vinted** : le scan et la sauvegarde ne font que lire (aucun clic, aucune saisie, aucune soumission), la validation ne contacte pas Vinted. Durée totale : environ 2 h, surtout de l'attente (scan ≈ 35 min, sauvegarde ≈ 1 h pour 146 annonces).

Cocher chaque case ; noter tout écart dans la section « Écarts » en bas, avec l'identifiant de l'annonce. Un écart se traite par une mise à jour de la spec concernée, pas par un correctif direct.

## 0. Préparation
- [ ] `docker compose up -d`, puis `mvn spring-boot:run` (profil local).
- [ ] `http://localhost:8080/vinted-session.html` : « Se connecter », se connecter soi-même dans le Chrome piloté, la page indique « connecté ».
- [ ] Choisir et noter 5 annonces témoins, ouvertes dans un autre navigateur (pas le Chrome piloté) :
  - A : plusieurs photos (au moins 3) ;
  - B : description longue (plusieurs paragraphes) ;
  - C : beaucoup de vues ou de favoris ;
  - D : réservée ou masquée, s'il y en a une ;
  - E : une annonce ordinaire.

| Témoin | Identifiant Vinted | Titre | Prix | Photos | Vues | Favoris |
|---|---|---|---|---|---|---|
| A | | | | | | |
| B | | | | | | |
| C | | | | | | |
| D | | | | | | |
| E | | | | | | |

Relever vues et favoris sur la page de **son dressing** (connecté), juste avant le scan.

## 1. Scan avec reprise (US-04, US-07)
`http://localhost:8080/wardrobe.html`. Laisser l'onglet du scan visible pendant le relevé.
- [ ] « Scanner », puis arrêter l'application (Ctrl+C) après 3 ou 4 annonces lues.
- [ ] Relancer `mvn spring-boot:run`, « Scanner » : le scan reprend sans relire les annonces déjà lues, et se termine sans problème affiché.
- [ ] Le nombre d'annonces affiché est celui de l'en-tête du dressing sur Vinted (annonces masquées comprises ou non : noter ce qui est vu).
- [ ] Titres et prix des 5 témoins identiques à Vinted.
- [ ] D (réservée) : noter comment elle apparaît (présente ou non).
- [ ] AT-07-10 : vues et favoris des 5 témoins identiques au relevé de l'étape 0 (« 0 vue · 0 favori » au singulier pour 0 et 1).

## 2. Sauvegarde avec reprise (US-05, US-06)
- [ ] « Sauvegarder », puis arrêter l'application après 2 ou 3 annonces sauvegardées.
- [ ] Relancer, « Sauvegarder » : la sauvegarde reprend sans refaire les annonces déjà sauvegardées, et se termine sans problème affiché.
- [ ] Chaque annonce affiche « sauvegardée le … · N photos », N égal au nombre de photos sur Vinted pour les 5 témoins.
- [ ] D : noter le comportement (le formulaire d'une annonce non modifiable peut être traité en « session non connectée » ; c'est un écart à remonter).
- [ ] Dans `data/photos/<identifiant de A>/` : autant de fichiers que de photos, qui s'ouvrent, en pleine taille (ex. 1200 × 1600, pas 600 × 800).
- [ ] Sur Vinted, aucune annonce n'a changé (date de modification, contenu des témoins).

## 3. Validation (US-08, AT-08-15)
Depuis le dressing, « Voir la sauvegarde » sur chaque témoin :
- [ ] « Vérification réussie » pour A, B, C, E. **Si elles échouent toutes sur le titre** : la comparaison exacte titre du scan / titre du formulaire ne tient pas, noter les deux valeurs affichées (point prioritaire d'AT-08-15).
- [ ] Photos de A affichées en grand, dans l'ordre de Vinted, la couverture en premier.
- [ ] Contenu de B : description complète (paragraphes et retours à la ligne), catégorie, marque, état, taille, couleurs, matières, format du colis identiques au formulaire de modification sur Vinted.
- [ ] « Valider » sur E : « Validée le … » s'affiche, puis « validée le … » sur la ligne de E dans le dressing.
- [ ] Contrôle d'intégrité : copier un fichier de `data/photos/<identifiant de A>/` ailleurs, le supprimer, rouvrir l'aperçu de A → « photo N manquante », bouton « Valider » inactif ; remettre le fichier → « Vérification réussie ».
- [ ] Annulation : relancer « Sauvegarder » (la sauvegarde complète recommence), puis rouvrir E → n'est plus validée.

## 4. Fin
- [ ] Fermer le Chrome piloté (tant qu'il est ouvert, un programme local peut piloter le compte connecté).
- [ ] Reporter le résultat dans les specs : AT-05-17, AT-06-12, AT-07-10, AT-08-15 « passé, confirmé par l'utilisateur le … », ou les écarts.

## Écarts
| Étape | Annonce | Attendu | Constaté |
|---|---|---|---|
| | | | |
