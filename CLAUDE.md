# Herois_baiduu (mod_id `flash`) - regras do projeto

- Responder ao usuario em portugues e avisar o passo atual enquanto trabalha.
- Forge 1.20.1 (47.4.10), mapeamentos oficiais, JDK 17. Compilar pelo workflow "Compilar jar" (branch `ajustes-v7`).

## Todo poder novo precisa sair e voltar pela seringa

Sempre que um poder novo for adicionado ao jogo:

1. Adicionar um valor em `com.flash.power.HeroPowers.Hero` (has, temporary, level, revoke, grantNew, restore).
2. Criar a capsula do poder em `ModItems` (`<poder>_essence_capsule`), com textura, modelo de item e nomes pt_br/en_us.
3. Registrar o `SyringeContent` em `com.flash.item.SyringeContents` com um `modelIndex` novo, e o override
   correspondente em `models/item/syringe.json` (+ `models/item/syringe_<id>.json` e a textura).
4. Nas checagens de "outro poder chegou depois", usar `HeroPowers.hasOther(p, Hero.<ESTE>)`, nunca `hasAny`.
5. Colocar a capsula numa aba do criativo.

Assim a seringa vazia tira o poder para a capsula e a capsula numa seringa devolve o poder.
