/*
 * e-mine · affiche le message demandé par l'application à l'ouverture de la page de connexion
 * (paramètre « motif » de l'adresse : deconnecte, expiree, erreur). Les messages sont rendus,
 * cachés et traduits, par le gabarit ; seul un motif connu en révèle un. Sans JavaScript, la
 * page fonctionne sans le message.
 */
(function () {
  "use strict";
  function afficher() {
    var motif = new URLSearchParams(window.location.search).get("motif");
    if (!motif || !/^[a-z]{1,20}$/.test(motif)) return;
    var message = document.querySelector('.emine-motif[data-motif="' + motif + '"]');
    if (message) message.hidden = false;
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", afficher);
  } else {
    afficher();
  }
})();
