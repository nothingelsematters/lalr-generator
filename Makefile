.PHONY: all run clean

PACKAGE = lalr
FOLDER = src/$(PACKAGE)
SOURCES = $(FOLDER)/InputGrammar.kt $(FOLDER)/LexicalAnalyzer.kt  $(FOLDER)/SyntaxAnalyzer.kt  $(FOLDER)/Utils.kt $(FOLDER)/ParserGenerator.kt
GENERATED = $(patsubst src/%.kt,$(OUT)/%Kt.class,$(SOURCES))

INPUT = $(FOLDER)/Main.kt $(FOLDER)/input/InputLexer.kt $(FOLDER)/input/InputParser.kt $(FOLDER)/Utils.kt $(FOLDER)/ParserGenerator.kt
PREPARATION = $(OUT)/$(PACKAGE)/MainKt.class
OUT = out


all: $(GENERATED) $(PREPARATION)


$(PREPARATION): $(GENERATED)
	kotlin -cp $(OUT) lalr.InputGrammarKt
	kotlinc -d out -cp $(OUT) $(INPUT)

$(GENERATED): $(SOURCES)
	kotlinc -d $(OUT) $(SOURCES)

run:
	kotlin -cp $(OUT) MainKt

clean:
	rm -rf out $(FOLDER)/input
