# Hand of God (Mão de Deus) — embutido no Herois_baiduu

O mod **Hand of God**, criado por **@onikoox**, vai embutido neste mod (META-INF/jarjar) com autorização do autor, para os poderes da Yas. Todos os direitos dele são reservados ao autor. Abaixo, os créditos originais que acompanham o mod:

---

# Créditos

**Hand of God (Mão de Deus)** — criado por **@onikoox**. Todos os direitos reservados (ver `LICENSE`).
Os avisos abaixo são das partes de terceiros, que continuam sob as licenças delas.

## Orbital Railgun (referência visual e de lógica)

Partes da matemática e da lógica deste mod (superfície do sol, coroa, rastro e brilho do sol
caindo em `strike.fsh`; orçamento de blocos por tick e blocos voando em
`OrbitalRailgunStrikeManager`) são adaptadas do mod Orbital Railgun 1.4.0 (Fabric 1.21.11),
distribuído sob a licença MIT:

```
The MIT License (MIT)

Copyright (c) 2025 Mudcrow and Gw Brother

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

Até a etapa 0 nenhum código do Orbital Railgun foi incorporado; a adaptação começa na etapa 2
(visual do sol) e na etapa 3 (impacto).

## Solas Shader

A fórmula da posição do sol do Solas (`CeuCliente.anguloSolas`) reproduz em Java as contas de
`shaders.properties` (timeAngle) e `deferred1.glsl` do Solas Shader V3.7b, só para posicionar o
sol-entidade sobre o sol do pack. Nenhum arquivo do Solas é distribuído com este mod.

O patch (`handofgod.ceu.PatchSolas`) roda na máquina do jogador: lê o zip do Solas V3.7b que ele já
tem em `shaderpacks/` (conferido pelo SHA-256), e grava ao lado uma cópia com outro nome
("Solas V3.7b + HandOfGod.zip") que lê os uniforms do mod. O zip original nunca é alterado.

A máscara de fase da Lua (`SuperficieLunar.mascaraFase` e `SolRenderer.lascaLua`) imita o que o
`drawSunMoon` do Solas faz com a Lua (disco liso apagado por um segundo disco deslocado), também
só para a troca invisível ficar igual ao céu do pack.

## Lua (rodada 5)

A superfície da Lua na mão é procedural (ruído e crateras gerados pelo próprio mod em
`SuperficieLunar`); não usa textura de terceiros. Na troca invisível sem shader o mod desenha a
própria textura `moon_phases.png` do jogo, que já vem com o Minecraft (não é distribuída com o mod).

## Texturas e ícones

As texturas do mod (`textures/entity/*.png`) são geradas pelo próprio projeto (ruído e gradientes) e os
ícones do painel de habilidades são pixel art feita à mão no código (`client/Icones.java`). Nenhuma
textura da NASA ou de terceiros é usada, e nenhuma imagem é gerada por IA.

## Sons

Todos os sons do mod (Sol e Lua) são sintetizados do zero por `sons/gerar_sons.py` (numpy/scipy),
sem amostras de terceiros.
