.PHONY: all run

PACKAGE = lalr
FOLDER = src/$(PACKAGE)
SOURCES = $(FOLDER)/InputGrammar.kt $(FOLDER)/LexicalAnalyzer.kt  $(FOLDER)/SyntaxAnalyzer.kt  $(FOLDER)/Utils.kt $(FOLDER)/ParserGenerator.kt
GENERATED = $(patsubst src/%.kt,$(OUT)/%Kt.class,$(SOURCES))

INPUT = $(FOLDER)/Main.kt
PREPARATION = $(patsubst src/%.kt,$(OUT)/%Kt.class,$(INPUT))
OUT = out


all: $(PREPARATION)


$(PREPARATION): $(GENERATED)
	kotlin -cp $(OUT) lalr.InputGrammarKt
	kotlinc -d out -cp $(OUT) $(INPUT)

$(GENERATED): $(SOURCES)
	kotlinc -d $(OUT) $(SOURCES)

run:
	kotlin -cp $(OUT) MainKt
