#!/bin/sh
# Render the local Keycloak realm from base + patch (P2) into /out/realm-local.json
# (the kc_realm_out volume, imported by the keycloak service).
# Run inside the mikefarah/yq container by the realm-builder compose service.
# Kept as a script file (not inline in compose.yml) so Docker Compose never
# interpolates the yq `$base`/`$p` variables.
set -e
yq -o=json eval-all '
  select(fi==0) as $base | select(fi==1) as $p
  | $base
  | .clients |= map(select(.clientId=="monteis-spa") .redirectUris = $p.spa.redirectUris | .webOrigins = $p.spa.webOrigins)
  | .groups |= map(
      . as $bg
      | (($p.groups // []) | map(select(.name == $bg.name)))[0] as $match
      | $bg | .subGroups = (($bg.subGroups // []) + ($match.subGroups // []))
    )
  | .groups += (
      ($p.groups // []) | map(select(
        .name as $n | ($base.groups // [] | map(.name)) | contains([$n]) | not
      ))
    )
  | .users = (($base.users // []) + ($p.users // []))
' /realm/realm-base.json /realm/patch.local.json > /out/realm-local.json
echo "rendered /out/realm-local.json"
