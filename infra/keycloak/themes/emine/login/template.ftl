<#--
  e-mine · disposition des pages de connexion Keycloak (thème « emine », Keycloak 26.8).
  Repris du gabarit de base (en-tête, scripts, messages, sélecteur de langue, macros) ; seuls
  l'icône d'onglet et le corps changent : écran partagé, identité de l'État à gauche (bandeau en
  haut sur mobile), formulaire à droite. Les formulaires eux-mêmes restent ceux de Keycloak
  (login.ftl…), stylés par css/emine.css.
-->
<#import "footer.ftl" as loginFooter>
<#import "theme-resources.ftl" as themeResourceTags>
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}" lang="${lang}"<#if realm.internationalizationEnabled> dir="${(locale.rtl)?then('rtl','ltr')}"</#if>>

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="robots" content="noindex, nofollow" />
    <meta name="theme-color" content="#0a1f44" />
    <title>${msg("emineTitreOnglet")}</title>
    <link rel="icon" type="image/svg+xml" href="${url.resourcesPath}/img/icone.svg" />
    <#if themeResources?? && themeResources.stylesCommon?has_content>
        <@themeResourceTags.renderStyles themeResources.stylesCommon url.resourcesCommonPath />
    <#elseif properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if themeResources?? && themeResources.styles?has_content>
        <@themeResourceTags.renderStyles themeResources.styles url.resourcesPath />
    <#elseif properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if themeResources?? && themeResources.scripts?has_content>
        <@themeResourceTags.renderScripts themeResources.scripts url.resourcesPath "text/javascript" />
    <#elseif properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript" defer></script>
        </#list>
    </#if>
    <script type="importmap">
        {
            "imports": {
                "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
            }
        }
    </script>
    <script src="${url.resourcesPath}/js/menu-button-links.js" type="module"></script>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="module">
        <#outputformat "JavaScript">
        import { startSessionPolling } from ${(url.resourcesPath + "/js/authChecker.js")?c};

        startSessionPolling(
            ${url.ssoLoginInOtherTabsUrl?c}
        );
        </#outputformat>
    </script>
    <#if authenticationSession??>
        <script type="module">
            <#outputformat "JavaScript">
            import { checkAuthSession } from ${(url.resourcesPath + "/js/authChecker.js")?c};

            checkAuthSession(
                ${authenticationSession.authSessionIdHash?c}
            );
            </#outputformat>
        </script>
    </#if>
</head>

<body class="${properties.kcBodyClass!} emine" data-page-id="login-${pageId}">
<a class="emine-evitement" href="#kc-content">${msg("emineAllerAuFormulaire")}</a>
<div class="emine-page">

    <#-- Identité de l'État : à gauche sur grand écran, bandeau en haut sur mobile. -->
    <header class="emine-identite">
        <div class="emine-tricolore" aria-hidden="true"><span></span><span></span><span></span></div>
        <div class="emine-identite-contenu">
            <#-- Emplacement de l'emblème officiel : visuel provisoire (aucun emblème réel en démonstration). -->
            <img class="emine-embleme" src="${url.resourcesPath}/img/embleme-provisoire.svg"
                 alt="${msg("emineEmblemeAlt")}" width="72" height="72" />
            <div class="emine-etat">
                <p class="emine-republique">${msg("emineRepublique")}</p>
                <p class="emine-devise">${msg("emineDevise")}</p>
            </div>
            <div class="emine-service">
                <p class="emine-ministere">${msg("emineMinistere")}</p>
                <p class="emine-marque">e-mine</p>
                <p class="emine-presentation">${msg("eminePresentation")}</p>
            </div>
        </div>
        <p class="emine-demo">${msg("emineDemo")}</p>
    </header>

    <main class="emine-formulaire">
      <div class="emine-carte">
        <div class="emine-entete-carte">
            <#if realm.internationalizationEnabled && locale.supported?size gt 1>
                <div class="${properties.kcLocaleMainClass!} emine-langues" id="kc-locale">
                    <div id="kc-locale-wrapper" class="${properties.kcLocaleWrapperClass!}">
                        <div id="kc-locale-dropdown" class="menu-button-links ${properties.kcLocaleDropDownClass!}">
                            <button tabindex="1" id="kc-current-locale-link" aria-label="${msg("languages")}" aria-haspopup="true" aria-expanded="false" aria-controls="language-switch1">${locale.current}</button>
                            <ul role="menu" tabindex="-1" aria-labelledby="kc-current-locale-link" aria-activedescendant="" id="language-switch1" class="${properties.kcLocaleListClass!}">
                                <#assign i = 1>
                                <#list locale.supported as l>
                                    <li class="${properties.kcLocaleListItemClass!}" role="none">
                                        <a role="menuitem" id="language-${i}" class="${properties.kcLocaleItemClass!}" href="${l.url}">${l.label}</a>
                                    </li>
                                    <#assign i++>
                                </#list>
                            </ul>
                        </div>
                    </div>
                </div>
            </#if>
        </div>

        <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
            <h1 id="kc-page-title"><#nested "header"></h1>
            <#if displayRequiredFields>
                <p class="emine-obligatoires"><span class="required">*</span> ${msg("requiredFields")}</p>
            </#if>
        <#else>
            <#nested "show-username">
            <div id="kc-username" class="${properties.kcFormGroupClass!}">
                <label id="kc-attempted-username">${auth.attemptedUsername}</label>
                <a id="reset-login" href="${url.loginRestartFlowUrl}" aria-label="${msg("restartLoginTooltip")}">
                    <div class="kc-login-tooltip">
                        <i class="${properties.kcResetFlowIcon!}"></i>
                        <span class="kc-tooltip-text">${msg("restartLoginTooltip")}</span>
                    </div>
                </a>
            </div>
        </#if>

        <div id="kc-content" tabindex="-1">
          <div id="kc-content-wrapper">

            <#-- Message demandé par l'application (motif=…) : révélé par js/motif.js, annoncé aux lecteurs d'écran. -->
            <#if !(message?has_content)>
                <div class="emine-motif emine-motif-succes" data-motif="deconnecte" role="status" hidden>
                    <strong>${msg("emineMotifDeconnecteTitre")}</strong> ${msg("emineMotifDeconnecteTexte")}
                </div>
                <div class="emine-motif emine-motif-info" data-motif="expiree" role="status" hidden>
                    <strong>${msg("emineMotifExpireeTitre")}</strong> ${msg("emineMotifExpireeTexte")}
                </div>
                <div class="emine-motif emine-motif-erreur" data-motif="erreur" role="alert" hidden>
                    <strong>${msg("emineMotifErreurTitre")}</strong> ${msg("emineMotifErreurTexte")}
                </div>
            </#if>

            <#-- Les actions demandées par l'application ne voient pas d'avertissement sur l'action à faire. -->
            <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="alert-${message.type} ${properties.kcAlertClass!} pf-m-<#if message.type = 'error'>danger<#else>${message.type}</#if>"
                     role="<#if message.type = 'error'>alert<#else>status</#if>">
                    <div class="pf-c-alert__icon">
                        <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}" aria-hidden="true"></span></#if>
                        <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}" aria-hidden="true"></span></#if>
                        <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}" aria-hidden="true"></span></#if>
                        <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}" aria-hidden="true"></span></#if>
                    </div>
                    <span class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink()>
                <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!}">
                        <input type="hidden" name="tryAnotherWay" value="on"/>
                        <a href="#" id="try-another-way"
                           onclick="document.forms['kc-select-try-another-way-form'].requestSubmit();return false;">${msg("doTryAnotherWay")}</a>
                    </div>
                </form>
            </#if>

            <#nested "socialProviders">

            <#if displayInfo>
                <div id="kc-info" class="${properties.kcSignUpClass!}">
                    <div id="kc-info-wrapper" class="${properties.kcInfoAreaWrapperClass!}">
                        <#nested "info">
                    </div>
                </div>
            </#if>
          </div>
        </div>

        <div class="emine-pied-carte">
            <p class="emine-securite">
                <svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>
                ${msg("emineConnexionSecurisee")}
            </p>
            <p class="emine-aide">${msg("emineAideQuestion")} <a href="${msg("emineAideLien")}">${msg("emineAideAction")}</a></p>
        </div>
      </div>
      <@loginFooter.content/>
    </main>
</div>
</body>
</html>
</#macro>
