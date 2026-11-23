# Dono dos volumes do Caddy (SEC-004). Arquivo para ser lido com `.` (sourced) por local.sh e deploy.sh, sempre a
# partir da raiz do repositório.
#
# A imagem `web` roda como 1000:1000. Volumes criados quando ela ainda rodava como root (certificados do Let's Encrypt
# em caddy-data) continuam do root e o Caddy não conseguiria renová-los. Este passo é idempotente e não apaga nada.
#
#   fix_web_volume_owner "<comando do compose>"

fix_web_volume_owner() {
  $1 run --rm --no-deps -T --user 0:0 --cap-add CHOWN --entrypoint chown web -R 1000:1000 /data /config
}
