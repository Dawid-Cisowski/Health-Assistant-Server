-- =============================================================================
-- V67: Polish name fixes (where name_pl was just the code acronym)
--      + short Polish descriptions for ALL marker_definitions.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Fix name_pl where it equals the raw code / acronym
-- ---------------------------------------------------------------------------

-- Morphology indices
UPDATE marker_definitions SET name_pl = 'Średnia objętość krwinki (MCV)'           WHERE code = 'MCV';
UPDATE marker_definitions SET name_pl = 'Średnia masa hemoglobiny w krwince (MCH)' WHERE code = 'MCH';
UPDATE marker_definitions SET name_pl = 'Średnie stężenie hemoglobiny (MCHC)'      WHERE code = 'MCHC';
UPDATE marker_definitions SET name_pl = 'Rozpiętość rozkładu erytrocytów SD'       WHERE code = 'RDW_SD';
UPDATE marker_definitions SET name_pl = 'Rozpiętość rozkładu erytrocytów CV'       WHERE code = 'RDW_CV';
UPDATE marker_definitions SET name_pl = 'Szerokość rozkładu płytek (PDW)'          WHERE code = 'PDW';
UPDATE marker_definitions SET name_pl = 'Średnia objętość płytki (MPV)'            WHERE code = 'MPV';
UPDATE marker_definitions SET name_pl = 'Odsetek dużych płytek (P-LCR)'            WHERE code = 'PLCR';
UPDATE marker_definitions SET name_pl = 'Płytkokryt (PCT)'                         WHERE code = 'PCT';
UPDATE marker_definitions SET name_pl = 'Jądrzaste krwinki czerwone (NRBC#)'       WHERE code = 'NRBC';
UPDATE marker_definitions SET name_pl = 'Odsetek jądrzastych krwinek (NRBC%)'      WHERE code = 'NRBC_PERC';

-- Thyroid
UPDATE marker_definitions SET name_pl = 'Hormon tyreotropowy (TSH)'                WHERE code = 'TSH';

-- Liver
UPDATE marker_definitions SET name_pl = 'Gamma-glutamylotransferaza (GGT)'         WHERE code = 'GGT';
UPDATE marker_definitions SET name_pl = 'Fosfataza alkaliczna (ALP)'               WHERE code = 'ALP';

-- Kidney
UPDATE marker_definitions SET name_pl = 'Szacunkowy wskaźnik filtracji (eGFR)'     WHERE code = 'EGFR';

-- Vitamins
UPDATE marker_definitions SET name_pl = 'Całkowita zdolność wiązania żelaza (TIBC)' WHERE code = 'TIBC';

-- Coagulation
UPDATE marker_definitions SET name_pl = 'Znormalizowany wskaźnik protrombiny (INR)' WHERE code = 'INR';

-- Cardiac biomarkers
UPDATE marker_definitions SET name_pl = 'Mózgowy peptyd natriuretyczny (BNP)'      WHERE code = 'BNP';
UPDATE marker_definitions SET name_pl = 'Frakcja MB kinazy kreatynowej (CK-MB)'    WHERE code = 'CK_MB';

-- Hormones
UPDATE marker_definitions SET name_pl = 'Hormon folikulotropowy (FSH)'             WHERE code = 'FSH';
UPDATE marker_definitions SET name_pl = 'Hormon luteinizujący (LH)'                WHERE code = 'LH';
UPDATE marker_definitions SET name_pl = 'Siarczan dehydroepiandrosteronu (DHEA-S)' WHERE code = 'DHEAS';
UPDATE marker_definitions SET name_pl = 'Globulina wiążąca hormony płciowe (SHBG)' WHERE code = 'SHBG';

-- Serology
UPDATE marker_definitions SET name_pl = 'Antystreptolizyna O (ASO)'                WHERE code = 'ASO';

-- Immunology
UPDATE marker_definitions SET name_pl = 'Immunoglobulina A (IgA)'                  WHERE code = 'IGA';
UPDATE marker_definitions SET name_pl = 'Immunoglobulina G (IgG)'                  WHERE code = 'IGG';
UPDATE marker_definitions SET name_pl = 'Immunoglobulina M (IgM)'                  WHERE code = 'IGM';

-- ---------------------------------------------------------------------------
-- 2. Descriptions — MORPHOLOGY
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Leukocyty (białe krwinki) odpowiedzialne za odporność; ich liczba pomaga ocenić infekcje i choroby układu immunologicznego.'
    WHERE code = 'WBC';
UPDATE marker_definitions SET description = 'Erytrocyty (krwinki czerwone) transportujące tlen do tkanek; kluczowy wskaźnik w diagnostyce niedokrwistości.'
    WHERE code = 'RBC';
UPDATE marker_definitions SET description = 'Hemoglobina — białko w erytrocytach przenoszące tlen; niskie stężenie wskazuje na niedokrwistość.'
    WHERE code = 'HGB';
UPDATE marker_definitions SET description = 'Hematokryt — odsetek objętości krwi zajęty przez erytrocyty; miernik zagęszczenia krwi i stopnia anemii.'
    WHERE code = 'HCT';
UPDATE marker_definitions SET description = 'Średnia objętość krwinki czerwonej pozwala rozróżnić rodzaje anemii — mikrocytarną (MCV↓), normocytarną i makrocytarną (MCV↑).'
    WHERE code = 'MCV';
UPDATE marker_definitions SET description = 'Średnia ilość hemoglobiny w jednej krwince; pomaga klasyfikować rodzaj niedokrwistości.'
    WHERE code = 'MCH';
UPDATE marker_definitions SET description = 'Średnie stężenie hemoglobiny w erytrocycie; obniżone w anemii niedoborowej, podwyższone w sferocytozie.'
    WHERE code = 'MCHC';
UPDATE marker_definitions SET description = 'Płytki krwi uczestniczące w procesie krzepnięcia; niedobór grozi krwawieniami, nadmiar może sprzyjać zakrzepicy.'
    WHERE code = 'PLT';
UPDATE marker_definitions SET description = 'Neutrofile (odsetek) — główne granulocyty zwalczające bakterie; wzrost wskazuje typowo na infekcję bakteryjną lub zapalenie.'
    WHERE code = 'NEUT';
UPDATE marker_definitions SET description = 'Limfocyty (odsetek) — komórki odpornościowe kluczowe dla odporności nabytej; wzrost typowy dla infekcji wirusowych.'
    WHERE code = 'LYMPH';
UPDATE marker_definitions SET description = 'Monocyty (odsetek) — komórki fagocytujące; wzrost obserwowany w infekcjach przewlekłych i chorobach zapalnych.'
    WHERE code = 'MONO';
UPDATE marker_definitions SET description = 'Eozynofile (odsetek) — granulocyty związane z alergiami i pasożytami; wzrost sygnalizuje atopię lub zakażenie pasożytnicze.'
    WHERE code = 'EOS';
UPDATE marker_definitions SET description = 'Bazofile (odsetek) — rzadkie granulocyty regulujące reakcje alergiczne; wzrost istotny w diagnostyce chorób mieloproliferacyjnych.'
    WHERE code = 'BASO';
UPDATE marker_definitions SET description = 'Neutrofile (wartość bezwzględna); ocena ryzyka infekcji po chemioterapii lub w neutropenii z innych przyczyn.'
    WHERE code = 'NEUT_ABS';
UPDATE marker_definitions SET description = 'Limfocyty (wartość bezwzględna); kluczowe w monitorowaniu układu odpornościowego i skuteczności leczenia immunosupresyjnego.'
    WHERE code = 'LYMPH_ABS';
UPDATE marker_definitions SET description = 'Monocyty (wartość bezwzględna); wzrost może wskazywać na infekcje przewlekłe lub schorzenia szpiku kostnego.'
    WHERE code = 'MONO_ABS';
UPDATE marker_definitions SET description = 'Eozynofile (wartość bezwzględna); podwyższone w alergiach, astmie oskrzelowej i zakażeniach pasożytniczych.'
    WHERE code = 'EOS_ABS';
UPDATE marker_definitions SET description = 'Bazofile (wartość bezwzględna); monitorowane głównie w przebiegu chorób mieloproliferacyjnych.'
    WHERE code = 'BASO_ABS';
UPDATE marker_definitions SET description = 'Bezwzględna szerokość rozkładu objętości erytrocytów; wskazuje na niejednorodność populacji czerwonych krwinek (anizocytoza).'
    WHERE code = 'RDW_SD';
UPDATE marker_definitions SET description = 'Procentowa zmienność objętości erytrocytów; wzrost może wyprzedzać zmiany w MCV w anemii niedoborowej.'
    WHERE code = 'RDW_CV';
UPDATE marker_definitions SET description = 'Szerokość rozkładu objętości płytek; wzrost może sugerować aktywację lub niejednorodność płytek krwi.'
    WHERE code = 'PDW';
UPDATE marker_definitions SET description = 'Średnia objętość płytki krwi; większe płytki są bardziej aktywne metabolicznie i mogą wskazywać na ryzyko sercowo-naczyniowe.'
    WHERE code = 'MPV';
UPDATE marker_definitions SET description = 'Odsetek dużych płytek krwi; podwyższony w trombocytopenii immunologicznej i przy aktywacji płytek.'
    WHERE code = 'PLCR';
UPDATE marker_definitions SET description = 'Płytkokryt — analogia do hematokrytu dla płytek; niska wartość towarzyszy trombocytopenii.'
    WHERE code = 'PCT';
UPDATE marker_definitions SET description = 'Odsetek niedojrzałych granulocytów w rozmazie; wzrost sugeruje aktywne pobudzenie szpiku (infekcja, sepsa).'
    WHERE code = 'IG_PERC';
UPDATE marker_definitions SET description = 'Bezwzględna liczba niedojrzałych granulocytów; marker aktywności szpiku i wczesnego zakażenia bakteryjnego.'
    WHERE code = 'IG_ABS';
UPDATE marker_definitions SET description = 'Bezwzględna liczba jądrzastych krwinek czerwonych; ich obecność we krwi obwodowej wskazuje na ciężki stres hematopoetyczny.'
    WHERE code = 'NRBC';
UPDATE marker_definitions SET description = 'Odsetek jądrzastych erytrocytów; podwyższony w hemolitycznych przełomach lub po usunięciu śledziony.'
    WHERE code = 'NRBC_PERC';

-- ---------------------------------------------------------------------------
-- LIPID_PANEL
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Cholesterol całkowity — suma wszystkich frakcji lipoproteinowych; kluczowy wskaźnik ryzyka chorób sercowo-naczyniowych.'
    WHERE code = 'CHOL';
UPDATE marker_definitions SET description = 'Cholesterol LDL ("zły") — główny nośnik odkładający się w ścianach naczyń; podstawowy cel terapii statynami.'
    WHERE code = 'LDL';
UPDATE marker_definitions SET description = 'Cholesterol HDL ("dobry") — usuwa cholesterol z tkanek; wyższe wartości obniżają ryzyko sercowo-naczyniowe.'
    WHERE code = 'HDL';
UPDATE marker_definitions SET description = 'Trójglicerydy — tłuszcze magazynowane w tkance tłuszczowej; wzrost przy cukrzycy, otyłości i nadużywaniu alkoholu.'
    WHERE code = 'TG';
UPDATE marker_definitions SET description = 'Cholesterol nie-HDL obejmuje wszystkie aterogenne frakcje (LDL + VLDL + IDL + Lp(a)); trafniejszy cel terapii niż sam LDL.'
    WHERE code = 'NON_HDL';
UPDATE marker_definitions SET description = 'Lipoproteina(a) — genetycznie uwarunkowany niezależny czynnik ryzyka miażdżycy i zakrzepicy naczyń.'
    WHERE code = 'LPA';
UPDATE marker_definitions SET description = 'Apolipoproteina B — białko nośnikowe aterogennych lipoprotein; dokładniejszy marker ryzyka sercowego niż stężenie LDL-C.'
    WHERE code = 'APO_B';
UPDATE marker_definitions SET description = 'Apolipoproteina A1 — główne białko frakcji HDL; marker zdolności odwrotnego transportu cholesterolu do wątroby.'
    WHERE code = 'APO_A1';

-- ---------------------------------------------------------------------------
-- THYROID
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Hormon tyreotropowy (TSH) wydzielany przez przysadkę — podstawowy test przesiewowy tarczycy; wzrost → niedoczynność, spadek → nadczynność.'
    WHERE code = 'TSH';
UPDATE marker_definitions SET description = 'Wolna trijodotyronina (FT3) — aktywna biologicznie forma hormonu tarczycy; bezpośrednio reguluje metabolizm komórkowy.'
    WHERE code = 'FT3';
UPDATE marker_definitions SET description = 'Wolna tyroksyna (FT4) — produkowana przez tarczycę, przekształcana w FT3 w tkankach; odzwierciedla rezerwy wydzielnicze gruczołu.'
    WHERE code = 'FT4';
UPDATE marker_definitions SET description = 'Przeciwciała przeciwko peroksydazie tarczycowej; podwyższone w chorobie Hashimoto i chorobie Gravesa-Basedowa.'
    WHERE code = 'ANTI_TPO';
UPDATE marker_definitions SET description = 'Przeciwciała przeciwko tyreoglobulinie; marker autoimmunologicznego zapalenia tarczycy i uzupełniający marker po leczeniu raka tarczycy.'
    WHERE code = 'ANTI_TG';

-- ---------------------------------------------------------------------------
-- GLUCOSE
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Glukoza na czczo — podstawowy test oceny gospodarki węglowodanowej; wyniki ≥126 mg/dL sugerują cukrzycę, 100–125 mg/dL — stan przedcukrzycowy.'
    WHERE code = 'GLU';
UPDATE marker_definitions SET description = 'Hemoglobina glikowana (HbA1c) — odzwierciedla średnie stężenie glukozy z ostatnich 2–3 miesięcy; kluczowy wskaźnik wyrównania cukrzycy.'
    WHERE code = 'HBA1C';
UPDATE marker_definitions SET description = 'Insulina — hormon trzustki regulujący poziom glukozy; stosowana do obliczenia indeksu insulinooporności HOMA-IR.'
    WHERE code = 'INSULIN';
UPDATE marker_definitions SET description = 'HbA1c wyrażona w jednostkach IFCC (mmol/mol) — standard europejski; ekwiwalent wartości procentowej (HbA1c % × 10.93 − 23.50).'
    WHERE code = 'HBA1C_IFCC';
UPDATE marker_definitions SET description = 'Insulina na czczo stosowana do obliczenia indeksu HOMA-IR; wzrost wskazuje na insulinooporność charakterystyczną dla zespołu metabolicznego.'
    WHERE code = 'INSULIN_FASTING';

-- ---------------------------------------------------------------------------
-- LIVER_PANEL
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Aminotransferaza alaninowa (ALT) — enzym wewnątrzkomórkowy wątroby; podwyższona przy zapaleniu lub stłuszczeniu wątroby.'
    WHERE code = 'ALT';
UPDATE marker_definitions SET description = 'Aminotransferaza asparaginowa (AST) — enzym obecny w wątrobie, mięśniach i sercu; wzrost sugeruje uszkodzenie tych tkanek.'
    WHERE code = 'AST';
UPDATE marker_definitions SET description = 'Gamma-glutamylotransferaza (GGT) — czuły wskaźnik chorób wątroby, dróg żółciowych i nadużywania alkoholu.'
    WHERE code = 'GGT';
UPDATE marker_definitions SET description = 'Bilirubina całkowita — produkt rozpadu hemoglobiny; podwyższona w chorobach wątroby, żółtaczce i nadmiernej hemolizie.'
    WHERE code = 'BILIR';
UPDATE marker_definitions SET description = 'Fosfataza alkaliczna (ALP) — enzym obecny w wątrobie i kościach; wzrost przy cholestazie, chorobach kości i ciąży.'
    WHERE code = 'ALP';
UPDATE marker_definitions SET description = 'Albumina — główne białko osocza syntetyzowane przez wątrobę; spada w niewydolności wątroby, niedożywieniu i chorobach nerek.'
    WHERE code = 'ALBUMIN';

-- ---------------------------------------------------------------------------
-- KIDNEY_PANEL
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Kreatynina — produkt metabolizmu mięśni wydalany przez nerki; wzrost stężenia wskazuje na upośledzoną filtrację kłębuszkową.'
    WHERE code = 'CREAT';
UPDATE marker_definitions SET description = 'Mocznik — końcowy produkt metabolizmu białek wydalany przez nerki; wzrost w niewydolności nerek i przy diecie bogatobiałkowej.'
    WHERE code = 'UREA';
UPDATE marker_definitions SET description = 'Kwas moczowy — produkt rozpadu puryn; podwyższony w dnie moczanowej, chorobach nerek i przy diecie bogatej w mięso.'
    WHERE code = 'UA';
UPDATE marker_definitions SET description = 'Szacunkowy wskaźnik filtracji kłębuszkowej (eGFR) obliczany ze stężenia kreatyniny; bezpośrednio odzwierciedla czynność nerek.'
    WHERE code = 'EGFR';

-- ---------------------------------------------------------------------------
-- INFLAMMATION
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Białko C-reaktywne (CRP) wytwarzane przez wątrobę; wzrost sygnalizuje ostre zapalenie, infekcję lub uszkodzenie tkanek.'
    WHERE code = 'CRP';
UPDATE marker_definitions SET description = 'Odczyn Biernackiego (OB) — szybkość opadania krwinek; nieswoista miara zapalenia, podwyższony w chorobach zapalnych i nowotworach.'
    WHERE code = 'ESR';
UPDATE marker_definitions SET description = 'Ferrytyna — białko magazynujące żelazo; niskie wartości wskazują na niedobór żelaza, wysokie mogą świadczyć o stanie zapalnym lub hemochromatozie.'
    WHERE code = 'FERR';

-- ---------------------------------------------------------------------------
-- VITAMINS / MINERALS
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Witamina D (25-OH) produkowana w skórze pod wpływem słońca; niedobór prowadzi do osłabienia kości, obniżenia odporności i depresji.'
    WHERE code = 'VIT_D';
UPDATE marker_definitions SET description = 'Witamina B12 — niezbędna do syntezy DNA i funkcji układu nerwowego; niedobór powoduje anemię megaloblastyczną i neuropatię.'
    WHERE code = 'VIT_B12';
UPDATE marker_definitions SET description = 'Żelazo — składnik hemoglobiny i mioglobiny; jego niedobór jest najczęstszą przyczyną anemii niedoborowej na świecie.'
    WHERE code = 'FE';
UPDATE marker_definitions SET description = 'Całkowita zdolność wiązania żelaza (TIBC) odzwierciedla poziom transferryny; wzrost w niedoborze żelaza, spadek w stanie zapalnym.'
    WHERE code = 'TIBC';
UPDATE marker_definitions SET description = 'Kwas foliowy (witamina B9) — niezbędny do syntezy DNA i podziałów komórkowych; kluczowy w ciąży (profilaktyka wad cewy nerwowej).'
    WHERE code = 'FOLIC_ACID';

-- ---------------------------------------------------------------------------
-- ALLERGY_PANEL
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Całkowite IgE — sumaryczny poziom immunoglobulin E; podwyższony w chorobach atopowych i zakażeniach pasożytniczych.'
    WHERE code = 'IGE_TOTAL';
UPDATE marker_definitions SET description = 'Swoiste IgE przeciwko konkretnemu alergenowi (pyłki, roztocza, pokarmy); pozwala zidentyfikować przyczynę alergii.'
    WHERE code = 'IGE_SPECIFIC';

-- ---------------------------------------------------------------------------
-- COAGULATION
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Znormalizowany wskaźnik protrombiny (INR) — standaryzowana miara krzepnięcia; kluczowy w monitorowaniu leczenia warfaryną (cel terapeutyczny 2–3).'
    WHERE code = 'INR';
UPDATE marker_definitions SET description = 'Czas protrombinowy (PT) — czas krzepnięcia przez zewnątrzpochodną drogę koagulacji; skrócony lub wydłużony wskazuje na zaburzenia hemostazy.'
    WHERE code = 'PT';
UPDATE marker_definitions SET description = 'Wskaźnik protrombiny wg Quicka — procentowa aktywność czynników krzepnięcia; stosowany jako alternatywa dla INR w Polsce.'
    WHERE code = 'PT_PERCENT';
UPDATE marker_definitions SET description = 'Czas częściowej tromboplastyny po aktywacji (APTT) — ocena wewnątrzpochodnej drogi krzepnięcia; stosowany do monitorowania heparyny niefrakcjonowanej.'
    WHERE code = 'APTT';
UPDATE marker_definitions SET description = 'Fibrynogen — białko krzepnięcia przekształcane w fibrynę; jednocześnie białko ostrej fazy; niski poziom grozi krwawieniami.'
    WHERE code = 'FIBRINOGEN';
UPDATE marker_definitions SET description = 'D-dimer — produkt rozpadu fibryny; wzrost wskazuje na aktywną zakrzepicę żylną, zatorowość płucną lub rozsiane wykrzepianie wewnątrznaczyniowe.'
    WHERE code = 'D_DIMER';

-- ---------------------------------------------------------------------------
-- ELECTROLYTES
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Wapń całkowity — niezbędny do skurczu mięśni, przewodnictwa nerwowego i mineralizacji kości; zaburzenia grożą tężyczką lub kamieniami nerkowymi.'
    WHERE code = 'CA';
UPDATE marker_definitions SET description = 'Magnez — kofaktor ponad 300 reakcji enzymatycznych; niedobór objawia się skurczami mięśni, zmęczeniem i zaburzeniami rytmu serca.'
    WHERE code = 'MG';
UPDATE marker_definitions SET description = 'Sód — główny kation płynu pozakomórkowego regulujący gospodarkę wodną i ciśnienie osmotyczne krwi.'
    WHERE code = 'NA';
UPDATE marker_definitions SET description = 'Potas — główny kation wewnątrzkomórkowy; jego zaburzenia (hipokaliemia i hiperkaliemia) grożą groźnymi arytmiami serca.'
    WHERE code = 'K';
UPDATE marker_definitions SET description = 'Fosfor nieorganiczny — kluczowy dla metabolizmu energetycznego (ATP) i mineralizacji kości; monitorowany w chorobach nerek i niedożywieniu.'
    WHERE code = 'P';
UPDATE marker_definitions SET description = 'Chlorki — anion regulujący równowagę kwasowo-zasadową i osmolalność osocza; oceniany łącznie z jonogramem w zaburzeniach elektrolitowych.'
    WHERE code = 'CL';

-- ---------------------------------------------------------------------------
-- CARDIAC_BIOMARKERS
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'NT pro-BNP uwalniany przez przeciążone komory serca; kluczowy marker diagnostyczny i prognostyczny niewydolności serca.'
    WHERE code = 'NT_PRO_BNP';
UPDATE marker_definitions SET description = 'Mózgowy peptyd natriuretyczny (BNP) wydzielany przez mięsień sercowy; wzrost wskazuje na przewodnienie i dysfunkcję skurczową lewej komory.'
    WHERE code = 'BNP';
UPDATE marker_definitions SET description = 'Troponina I — białko swoiste dla mięśnia sercowego uwalniane przy martwicy kardiomiocytów; podstawowy marker ostrego zawału serca.'
    WHERE code = 'TROPONIN_I';
UPDATE marker_definitions SET description = 'Troponina T (hs-cTnT) — analogicznie do TnI białko uwalniane przy uszkodzeniu kardiomiocytów; test wysokiej czułości umożliwia wczesne wykrycie zawału.'
    WHERE code = 'TROPONIN_T';
UPDATE marker_definitions SET description = 'Frakcja MB kinazy kreatynowej (CK-MB) — enzym swoisty dla mięśnia sercowego; historyczny marker zawału, uzupełniający wobec troponin.'
    WHERE code = 'CK_MB';

-- ---------------------------------------------------------------------------
-- HORMONES
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Testosteron — główny androgen u mężczyzn; ocena płodności, libido oraz diagnostyka hipogonadyzmu i hiperandrogenizmu u kobiet.'
    WHERE code = 'TESTOSTERONE';
UPDATE marker_definitions SET description = 'Estradiol (E2) — dominujący estrogen u kobiet; monitorowany w ocenie cyklu, protokołach IVF i diagnostyce menopauzy.'
    WHERE code = 'ESTRADIOL';
UPDATE marker_definitions SET description = 'Progesteron — hormon fazy lutealnej przygotowujący macicę do implantacji; ocena owulacji i funkcji ciałka żółtego.'
    WHERE code = 'PROGESTERONE';
UPDATE marker_definitions SET description = 'Hormon folikulotropowy (FSH) — stymuluje wzrost pęcherzyków jajnikowych u kobiet i spermatogenezę u mężczyzn; wzrost sugeruje wyczerpanie rezerwy jajnikowej.'
    WHERE code = 'FSH';
UPDATE marker_definitions SET description = 'Hormon luteinizujący (LH) — wyzwala owulację u kobiet; u mężczyzn stymuluje komórki Leydiga do produkcji testosteronu.'
    WHERE code = 'LH';
UPDATE marker_definitions SET description = 'Prolaktyna — hormon laktacji; nadmiar (hiperprolaktynemia) zaburza menstruację, płodność i może wskazywać na gruczolaka przysadki.'
    WHERE code = 'PROLACTIN';
UPDATE marker_definitions SET description = 'Kortyzol — główny hormon stresu wydzielany przez korę nadnerczy; ocena nadczynności (choroba Cushinga) i niedoczynności (choroba Addisona) nadnerczy.'
    WHERE code = 'CORTISOL';
UPDATE marker_definitions SET description = 'Siarczan dehydroepiandrosteronu (DHEA-S) produkowany przez nadnercza; marker rezerwy kory nadnerczy i androgenizacji u kobiet.'
    WHERE code = 'DHEAS';
UPDATE marker_definitions SET description = 'Globulina wiążąca hormony płciowe (SHBG) — transportuje testosteron i estradiol; jej poziom wpływa na biologiczną dostępność aktywnych hormonów.'
    WHERE code = 'SHBG';

-- ---------------------------------------------------------------------------
-- URINE
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Barwa moczu — wskaźnik jakościowy; mocz ciemny może sugerować odwodnienie, żółtaczkę lub krwiomocz.'
    WHERE code = 'URINE_COLOR';
UPDATE marker_definitions SET description = 'Przejrzystość moczu — mocz zmętniały może wskazywać na infekcję, obecność kryształów lub leukocyturię.'
    WHERE code = 'URINE_CLARITY';
UPDATE marker_definitions SET description = 'Odczyn (pH) moczu; zakwaszony w kwasicy metabolicznej i diecie bogatobiałkowej, zasadowy w infekcjach bakteryjnych.'
    WHERE code = 'URINE_PH';
UPDATE marker_definitions SET description = 'Ciężar właściwy moczu odzwierciedla zdolność zagęszczania nerek; niski w moczówce prostej, wysoki w odwodnieniu i proteinurii.'
    WHERE code = 'URINE_SG';
UPDATE marker_definitions SET description = 'Białko w moczu — zdrowe nerki nie przepuszczają białka; proteinuria wskazuje na uszkodzenie kłębuszków w chorobach nerek lub nadciśnieniu.'
    WHERE code = 'URINE_PROTEIN';
UPDATE marker_definitions SET description = 'Glukoza w moczu pojawia się po przekroczeniu progu nerkowego (~180 mg/dL) lub przy uszkodzeniu kanalików (Zespół Fanconiego).'
    WHERE code = 'URINE_GLUCOSE';
UPDATE marker_definitions SET description = 'Ketony w moczu świadczą o nasilonym spalaniu tłuszczów; obecne w cukrzycowej kwasicy ketonowej, głodzeniu i niskowęglowodanowej diecie.'
    WHERE code = 'URINE_KETONES';
UPDATE marker_definitions SET description = 'Azotyny w moczu powstają z azotanów w procesie bakteryjnego metabolizmu; wynik dodatni silnie sugeruje infekcję bakteryjną dróg moczowych.'
    WHERE code = 'URINE_NITRITES';
UPDATE marker_definitions SET description = 'Erytrocyty w moczu (krwiomocz); wskazują na uszkodzenie nerek, kamicę moczową, infekcję lub nowotwór układu moczowego.'
    WHERE code = 'URINE_RBC';
UPDATE marker_definitions SET description = 'Leukocyty w moczu oznaczane paskiem (ropomocz); obecność sugeruje infekcję lub stan zapalny dróg moczowych.'
    WHERE code = 'URINE_WBC';
UPDATE marker_definitions SET description = 'Leukocyty w osadzie moczu z badania mikroskopowego; dokładniejszy marker leukocyturii niż wynik paskowy.'
    WHERE code = 'URINE_WBC_SEDIMENT';
UPDATE marker_definitions SET description = 'Bakterie w moczu; podwyższona liczba wskazuje na zakażenie układu moczowego wymagające potwierdzenia posiewem z antybiogramem.'
    WHERE code = 'URINE_BACTERIA';
UPDATE marker_definitions SET description = 'Bilirubina w moczu; jej obecność wskazuje na uszkodzenie hepatocytów lub cholestazę (żółtaczka wątrobowa i mechaniczna).'
    WHERE code = 'URINE_BILIRUBIN';
UPDATE marker_definitions SET description = 'Urobilinogen w moczu — produkt metabolizmu bilirubiny; podwyższony w nadmiernej hemolizie i chorobach wątroby, nieobecny przy pełnej cholestazie.'
    WHERE code = 'URINE_UROBILINOGEN';
UPDATE marker_definitions SET description = 'Wałeczki szkliste w osadzie moczu — cylindryczne struktury z białka; ich obecność wskazuje na chorobę miąższu nerek lub odwodnienie.'
    WHERE code = 'URINE_CASTS';
UPDATE marker_definitions SET description = 'Komórki nabłonka płaskiego w moczu; obecność może sugerować zanieczyszczenie próbki lub zapalenie dolnych dróg moczowych.'
    WHERE code = 'URINE_EPITHELIAL';
UPDATE marker_definitions SET description = 'Komórki nabłonka okrągłego (cewkowego) w osadzie; podwyższone wskazują na uszkodzenie kanalików nerkowych (np. martwica kanalików).'
    WHERE code = 'URINE_EPITHELIAL_ROUND';
UPDATE marker_definitions SET description = 'Śluz w moczu; niewielkie ilości są prawidłowe; nadmiar może wskazywać na podrażnienie lub stan zapalny dolnych dróg moczowych.'
    WHERE code = 'URINE_MUCUS';

-- ---------------------------------------------------------------------------
-- STOOL_TEST
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Krew utajona w kale — test przesiewowy w kierunku polipów i raka jelita grubego; wykrywa krew niewidoczną gołym okiem.'
    WHERE code = 'OCCULT_BLOOD';
UPDATE marker_definitions SET description = 'Kalprotektyna — białko neutrofilów w kale; czuły marker zapalenia jelita (choroba Leśniowskiego-Crohna, wrzodziejące zapalenie jelita grubego).'
    WHERE code = 'CALPROTECTIN';
UPDATE marker_definitions SET description = 'Antygen Giardia lamblia w kale; diagnostyka giardiozy (lambliozy) — pasożytniczej infekcji jelita cienkiego powodującej biegunki i malabsorpcję.'
    WHERE code = 'GIARDIA';

-- ---------------------------------------------------------------------------
-- SIBO
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Wyjściowe stężenie wodoru H2 w powietrzu wydychanym — punkt odniesienia dla interpretacji wyniku testu na przerost bakteryjny jelita cienkiego (SIBO).'
    WHERE code = 'H2_START';
UPDATE marker_definitions SET description = 'Przyrost stężenia wodoru H2 po podaniu laktulozy lub glukozy; wzrost >20 ppm we wczesnej fazie testu wskazuje na SIBO.'
    WHERE code = 'H2_DELTA';
UPDATE marker_definitions SET description = 'Maksymalne stężenie metanu CH4 w powietrzu wydychanym; wzrost >10 ppm wskazuje na IMO (rozrost metanogennych archeonów w jelicie).'
    WHERE code = 'CH4_MAX';
UPDATE marker_definitions SET description = 'Łączny przyrost H2 i CH4 w teście oddechowym; stosowany przy diagnostyce mieszanej formy SIBO/IMO gdy oba gazy są podwyższone.'
    WHERE code = 'H2_CH4_DELTA';

-- ---------------------------------------------------------------------------
-- SEROLOGY
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Antystreptolizyna O (ASO) — przeciwciało przeciwko toksynom paciorkowca; wzrost po infekcji paciorkowcowej, pomocne w diagnostyce gorączki reumatycznej.'
    WHERE code = 'ASO';
UPDATE marker_definitions SET description = 'Przeciwciała IgA przeciwko transglutaminazie tkankowej — podstawowy marker serologiczny celiakii; podwyższone przy czynnej nietolerancji glutenu.'
    WHERE code = 'ANTY_TGT_IGA';

-- ---------------------------------------------------------------------------
-- IMMUNOLOGY
-- ---------------------------------------------------------------------------
UPDATE marker_definitions SET description = 'Immunoglobulina A (IgA) — główna immunoglobulina wydzielnicza chroniąca błony śluzowe; niedobór IgA jest najczęstszą pierwotną immunodeficjencją.'
    WHERE code = 'IGA';
UPDATE marker_definitions SET description = 'Immunoglobulina G (IgG) — najobfitsza immunoglobulina surowicy; odpowiada za odporność humoralną i pamięć immunologiczną.'
    WHERE code = 'IGG';
UPDATE marker_definitions SET description = 'Immunoglobulina M (IgM) — pierwsza immunoglobulina wytwarzana po ekspozycji na antygen; wzrost wskazuje na świeżą infekcję lub pierwotną immunodeficjencję.'
    WHERE code = 'IGM';
