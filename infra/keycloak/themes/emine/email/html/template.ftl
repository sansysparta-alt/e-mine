<#--
  e-mine · mise en page des e-mails Keycloak : bandeau Bleu Souverain (République de Guinée,
  Ministère, e-mine), liseré aux couleurs nationales, contenu, pied (message automatique, données
  fictives). Tableaux et styles en ligne : rendu fiable dans les messageries.
-->
<#macro emailLayout>
<!DOCTYPE html>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>e-mine</title>
</head>
<body style="margin:0;padding:0;background:#f6f9fe;font-family:Calibri,Carlito,'Segoe UI',Arial,sans-serif;color:#0a1f44;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f6f9fe;padding:24px 12px;">
    <tr>
      <td align="center">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border:1px solid #c3d3ea;border-radius:12px;overflow:hidden;">
          <tr>
            <td style="padding:0;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                <tr>
                  <td style="height:5px;background:#ce1126;font-size:0;line-height:0;">&nbsp;</td>
                  <td style="height:5px;background:#fcd116;font-size:0;line-height:0;">&nbsp;</td>
                  <td style="height:5px;background:#009460;font-size:0;line-height:0;">&nbsp;</td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="background:#0a1f44;padding:20px 28px;color:#ffffff;">
              <p style="margin:0;font-family:Cambria,Georgia,serif;font-size:17px;font-weight:700;">${msg("emineRepublique")}</p>
              <p style="margin:2px 0 0;font-size:13px;font-weight:600;color:#c9a227;">${msg("emineDevise")}</p>
              <p style="margin:12px 0 0;font-size:14px;color:#dbe6f6;">${msg("emineMinistere")}</p>
              <p style="margin:2px 0 0;font-family:Cambria,Georgia,serif;font-size:24px;font-weight:700;">e-mine</p>
            </td>
          </tr>
          <tr>
            <td style="padding:28px;font-size:16px;line-height:1.55;color:#0a1f44;">
              <#nested>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 28px 24px;border-top:1px solid #c3d3ea;font-size:13px;line-height:1.5;color:#3b4a66;">
              <p style="margin:0;">${msg("emineAutomatique")}</p>
              <p style="margin:6px 0 0;">${msg("emineDemo")}</p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>
</#macro>
