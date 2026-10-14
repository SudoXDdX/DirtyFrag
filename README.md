# DirtyFrag Galaxy A57 5G

Root temporário para o Samsung Galaxy A57 5G (SM-A576B) sem bootloader desbloqueado, usando a primitiva de escrita no page cache do DirtyFrag (CVE-2026-43284) com um módulo de kernel compilado exatamente pra esse aparelho.

Isso nasceu de um fork do trabalho do diabl0w (DFRoot) e do mitschud (DirtyFrag Android), com duas mudanças principais que fizeram ele funcionar no One UI 8:

- o gatilho antigo esperava uma função da libc++ que nenhum processo privilegiado chamava no A57, então o hook nunca disparava. Aqui o alvo é o destrutor do LogMessage da libbase, que o init chama a toda hora. O hook dispara em segundos.
- o dfroot.ko embutido não é mais o genérico de DDK. Ele foi compilado contra a árvore do kernel 6.12.38 do próprio A576B, com vermagic idêntico ao do telefone (6.12.38-android16-5-p0e1ad48-abogkiA576BXXS6AZI2-4k) e os CRCs de modversions extraídos do vmlinux real da atualização de setembro (A576BXXS6AZI2). Isso remove qualquer chance de rejeição por vermagic na hora do insmod.

## Como funciona por dentro

1. O app pede ao IpSecManager uma transform inbound AES-CBC no loopback. Essa é uma API pública, do próprio framework, então nada de CAP_NET_ADMIN.
2. O exploit monta pacotes ESP onde o IV calculado transforma a descriptografia CBC em escrita arbitrária de 16 bytes por bloco. Primeiro ele reescreve o crash_dump64 com um helper de splice, que serve de ponte pra ler arquivos vendor que untrusted_app não enxerga.
3. Com essa ponte, o dfroot.ko é gravado dentro de um arquivo vendor (libbinderdebug.so) e o shellcode entra na libbase do APEX.
4. Na próxima vez que o init loga qualquer coisa, o destrutor do LogMessage pula pro shellcode, que confere uid 0 e tid 1, cria um mutex em /dev/df e executa insmod no domínio vendor_modprobe.
5. O módulo desliga o enforcing do SELinux, neutraliza o DEFEX por kprobe e sobe o bootstrap, que carrega o ksud do gerenciador de SU que você escolher.

Root é efêmero: reiniciar o telefone desfaz tudo e volta ao estado limpo.

## Estrutura

```
src/
  exp.c               exploit principal (primitiva CBC, patching, cadeia toda)
  libcxx.S            shellcode + dados do hook
  elf_parser.c        resolve o offset do símbolo alvo na libbase
  splicehelper.c      helper que roda dentro do crash_dump64
  bootstrap.c         o que roda como root depois do módulo
  aes256.h            AES puro em C pro cálculo do IV
  ko/                 módulos por KMI, incluindo o android16-6.12 compilado pro A57
  lkm/                fonte do dfroot.ko e Makefile
  SaHelper.java       cria a SA via IpSecManager fora do app (pra rodar do Termux)
  run-dirtyfrag-termux.sh   roda tudo sem instalar nada (ver abaixo)
```

## Rodando do Termux (sem instalar APK)

O SaHelper cria a mesma transform que o app criaria, usando o app_process com a API pública do IpSec. Depois o binário do exploit roda com os parâmetros que o helper deixou no disco:

```sh
sh src/run-dirtyfrag-termux.sh
```

## Compilando o módulo pra esse telefone

Precisa da árvore do kernel 6.12.38 do A576B (o drop opensource da Samsung serve), do .config com gki_defconfig + galaxy_ogki_defconfig, do vermagic correto em CONFIG_LOCALVERSION e de um Module.symvers com os CRCs reais. Os CRCs saem do vmlinux da atualização com o extract_target_symvers.py do Root-My-Galaxy. Com a árvore preparada (modules_prepare), é um make M= normal.

## Créditos

- diabl0w, pelo DFRoot e pela cadeia original
- mitschud, pelo DirtyFrag Android e pelo app base
- V4bel, pela pesquisa do DirtyFrag e pelo write-up da primitiva
- combeng6th, pelo DirtyInit, de onde veio a ideia do alvo no LogMessage
- lsposed, polygraphene e o restante do time original listado no README do DirtyFrag

Feito num Galaxy A57, num Termux, sem PC.
