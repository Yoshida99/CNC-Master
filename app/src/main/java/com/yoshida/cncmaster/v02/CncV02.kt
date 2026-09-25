package com.yoshida.cncmaster.v02

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class OperationType(val title: String) {
    ROUGH("Черновая"),
    FINISH("Чистовая"),
    BORING("Расточка"),
    GROOVING("Канавка"),
    PARTING("Отрезка")
}

enum class Strategy(val title: String) {
    STABLE("Стабильно"),
    BALANCED("Баланс"),
    PRODUCTIVE("Производительно")
}

enum class ThreadSide(val title: String) {
    EXTERNAL("Наружная"),
    INTERNAL("Внутренняя")
}

enum class ThreadHand(val title: String) {
    RIGHT("Правая"),
    LEFT("Левая")
}

data class MaterialPreset(
    val id: String,
    val name: String,
    val isoGroup: String,
    val aliases: String,
    val hardnessNote: String,
    val vcMin: Double,
    val vcMax: Double,
    val feedMin: Double,
    val feedMax: Double,
    val apMin: Double,
    val apMax: Double,
    val machiningNotes: List<String>,
    val sourceLabel: String,
)

data class CuttingRecommendation(
    val material: MaterialPreset,
    val operation: OperationType,
    val strategy: Strategy,
    val diameterMm: Double,
    val cuttingSpeedMMin: Double,
    val rpm: Int,
    val feedPerRevMm: Double,
    val feedRateMmMin: Int,
    val depthOfCutMm: Double,
    val warnings: List<String>,
)

data class MetricThreadGeometry(
    val nominalMm: Double,
    val pitchMm: Double,
    val pitchDiameterBasicMm: Double,
    val externalMinorBasicMm: Double,
    val internalMinorBasicMm: Double,
    val tapDrillApproxMm: Double,
    val radialThreadHeightMm: Double,
)

data class MetricThreadTableRow(
    val designation: String,
    val nominalMm: Double,
    val pitchMm: Double,
    val pitchDiameterMm: Double,
    val externalMinorMm: Double,
    val internalMinorMm: Double,
)

data class PipeThreadRow(
    val designation: String,
    val tpi: Int,
    val nominalOdMm: Double,
    val pitchMm: Double,
    val pitchDiameterMm: Double,
    val internalMinorMm: Double,
)

data class FanucThreadInput(
    val nominalDiameterMm: Double,
    val pitchMm: Double,
    val zEndMm: Double,
    val side: ThreadSide,
    val hand: ThreadHand,
    val finishPasses: Int = 2,
    val chamferTenthsLead: Int = 0,
    val minCutMm: Double = 0.05,
    val finishAllowanceMm: Double = 0.05,
    val firstCutMm: Double = 0.15,
)

data class FanucProgramResult(
    val g76: String,
    val g92: String,
    val calculatedFinalX: Double,
    val radialHeight: Double,
    val warnings: List<String>,
)

data class DiagnosticGuide(
    val id: String,
    val symptom: String,
    val description: String,
    val likelyCauses: List<String>,
    val checksFirst: List<String>,
    val actions: List<String>,
    val sourceLabel: String,
)

data class ToolReference(
    val title: String,
    val subtitle: String,
    val points: List<String>,
)

data class ToolLifeEntry(
    val id: String,
    val createdAt: Long,
    val insertName: String,
    val materialName: String,
    val vc: String,
    val feed: String,
    val ap: String,
    val partsCount: Int,
    val result: String,
)

data class PartCard(
    val id: String,
    val createdAt: Long,
    val name: String,
    val material: String,
    val machine: String,
    val programNumber: String,
    val cycleSeconds: Double,
    val notes: String,
)

object CncCatalog {
    val materials: List<MaterialPreset> = listOf(
        MaterialPreset(
            id = "A12",
            name = "A12 / автоматная сталь",
            isoGroup = "ISO P · free-cutting steel",
            aliases = "А12, 11SMn30-подобная группа",
            hardnessNote = "Обычно мягкая/средняя твёрдость",
            vcMin = 150.0,
            vcMax = 240.0,
            feedMin = 0.10,
            feedMax = 0.32,
            apMin = 0.4,
            apMax = 3.0,
            machiningNotes = listOf(
                "Хорошая обрабатываемость и обычно предсказуемая стружка.",
                "Для серийной работы выгоднее сначала стабилизировать стружколомание, затем поднимать скорость.",
                "Точные режимы должны сверяться с каталогом конкретной пластины."
            ),
            sourceLabel = "Seco SMG P1: free-cutting steels"
        ),
        MaterialPreset(
            id = "30HGSA_HARD",
            name = "30ХГСА · 35–40 HRC",
            isoGroup = "ISO P/H переходная зона",
            aliases = "30ХГСА, Cr-Mn-Si alloy steel",
            hardnessNote = "35–40 HRC",
            vcMin = 65.0,
            vcMax = 120.0,
            feedMin = 0.07,
            feedMax = 0.22,
            apMin = 0.25,
            apMax = 1.8,
            machiningNotes = listOf(
                "Нужна жёсткая система и минимальный вылет.",
                "При сколе кромки сначала проверяй жёсткость, затем снижай Vc и DOC.",
                "Для прерывистого реза приоритет у более вязкой марки пластины."
            ),
            sourceLabel = "Seco ISO P/H material behavior + conservative starting envelope"
        ),
        MaterialPreset(
            id = "L63",
            name = "Л63 / латунь",
            isoGroup = "ISO N · non-ferrous",
            aliases = "Л63, brass Cu-Zn",
            hardnessNote = "Цветной сплав",
            vcMin = 180.0,
            vcMax = 340.0,
            feedMin = 0.07,
            feedMax = 0.30,
            apMin = 0.3,
            apMax = 3.0,
            machiningNotes = listOf(
                "Предпочитает острую кромку и свободный сход стружки.",
                "Как правило допускает высокую скорость при достаточной жёсткости и мощности.",
                "Если появляется заусенец — проверь остроту кромки и фактическую подачу."
            ),
            sourceLabel = "Seco ISO N: copper/brass group"
        ),
        MaterialPreset(
            id = "304",
            name = "304 / 12Х18Н10Т-подобная аустенитная нержавейка",
            isoGroup = "ISO M · stainless",
            aliases = "AISI 304, 12Х18Н10Т (не полный эквивалент)",
            hardnessNote = "Аустенитная нержавеющая сталь",
            vcMin = 85.0,
            vcMax = 165.0,
            feedMin = 0.08,
            feedMax = 0.24,
            apMin = 0.3,
            apMax = 2.5,
            machiningNotes = listOf(
                "Склонна к наклёпу, наросту и зарубке по глубине резания.",
                "Не допускай длительного трения кромки без уверенного съёма материала.",
                "Стабильный подвод СОЖ и положительная геометрия часто помогают."
            ),
            sourceLabel = "Seco ISO M machining characteristics"
        ),
        MaterialPreset(
            id = "C45",
            name = "Сталь 45 / C45 / AISI 1045",
            isoGroup = "ISO P4",
            aliases = "45, C45E, AISI 1045",
            hardnessNote = "Нормализованная/улучшенная — зависит от состояния",
            vcMin = 120.0,
            vcMax = 220.0,
            feedMin = 0.10,
            feedMax = 0.32,
            apMin = 0.4,
            apMax = 3.0,
            machiningNotes = listOf(
                "Универсальная сталь для базовой настройки режимов.",
                "При нестабильной корке или поковке снижай скорость и бери более вязкую кромку.",
                "При чистовой обработке следи за соответствием подачи радиусу вершины."
            ),
            sourceLabel = "Seco SMG P4 reference material C45E"
        ),
        MaterialPreset(
            id = "42CRMO4",
            name = "42CrMo4 / 40Х-подобная легированная сталь",
            isoGroup = "ISO P5",
            aliases = "42CrMo4, 4140 family, 40Х — близкая по применению группа",
            hardnessNote = "Состояние сильно влияет на режим",
            vcMin = 95.0,
            vcMax = 180.0,
            feedMin = 0.09,
            feedMax = 0.28,
            apMin = 0.35,
            apMax = 2.5,
            machiningNotes = listOf(
                "Учитывай фактическую твёрдость после термообработки.",
                "При росте твёрдости смещайся к нижней границе Vc.",
                "Для прерывистого реза не гони скорость ценой сколов."
            ),
            sourceLabel = "Seco SMG P5 reference material 42CrMo4"
        )
    )

    val metricFine: List<MetricThreadTableRow> = listOf(
        MetricThreadTableRow("M6×0.75", 6.0, 0.75, 5.513, 5.080, 5.188),
        MetricThreadTableRow("M8×0.75", 8.0, 0.75, 7.513, 7.080, 7.188),
        MetricThreadTableRow("M8×1.0", 8.0, 1.0, 7.350, 6.773, 6.917),
        MetricThreadTableRow("M10×0.75", 10.0, 0.75, 9.513, 9.080, 9.188),
        MetricThreadTableRow("M10×1.0", 10.0, 1.0, 9.350, 8.773, 8.917),
        MetricThreadTableRow("M10×1.25", 10.0, 1.25, 9.188, 8.466, 8.647),
        MetricThreadTableRow("M12×1.0", 12.0, 1.0, 11.350, 10.773, 10.917),
        MetricThreadTableRow("M12×1.25", 12.0, 1.25, 11.188, 10.466, 10.647),
        MetricThreadTableRow("M12×1.5", 12.0, 1.5, 11.026, 10.160, 10.376),
        MetricThreadTableRow("M14×1.0", 14.0, 1.0, 13.350, 12.773, 12.917),
        MetricThreadTableRow("M14×1.25", 14.0, 1.25, 13.188, 12.466, 12.647),
        MetricThreadTableRow("M14×1.5", 14.0, 1.5, 13.026, 12.160, 12.376),
        MetricThreadTableRow("M16×1.0", 16.0, 1.0, 15.350, 14.773, 14.917),
        MetricThreadTableRow("M16×1.5", 16.0, 1.5, 15.026, 14.160, 14.376),
        MetricThreadTableRow("M18×1.5", 18.0, 1.5, 17.026, 16.160, 16.376),
        MetricThreadTableRow("M20×1.5", 20.0, 1.5, 19.026, 18.160, 18.376),
        MetricThreadTableRow("M20×2.0", 20.0, 2.0, 18.701, 17.546, 17.835),
        MetricThreadTableRow("M24×1.5", 24.0, 1.5, 23.026, 22.160, 22.376),
        MetricThreadTableRow("M24×2.0", 24.0, 2.0, 22.701, 21.546, 21.835),
        MetricThreadTableRow("M30×2.0", 30.0, 2.0, 28.701, 27.546, 27.835)
    )

    val coarsePitchByNominal: Map<Double, Double> = linkedMapOf(
        2.0 to 0.4,
        2.5 to 0.45,
        3.0 to 0.5,
        4.0 to 0.7,
        5.0 to 0.8,
        6.0 to 1.0,
        8.0 to 1.25,
        10.0 to 1.5,
        12.0 to 1.75,
        14.0 to 2.0,
        16.0 to 2.0,
        18.0 to 2.5,
        20.0 to 2.5,
        22.0 to 2.5,
        24.0 to 3.0,
        27.0 to 3.0,
        30.0 to 3.5,
        36.0 to 4.0,
    )

    val bspp: List<PipeThreadRow> = listOf(
        PipeThreadRow("G 1/16", 28, 7.723, 0.907, 7.142, 6.562),
        PipeThreadRow("G 1/8", 28, 9.728, 0.907, 9.147, 8.567),
        PipeThreadRow("G 1/4", 19, 13.157, 1.337, 12.301, 11.445),
        PipeThreadRow("G 3/8", 19, 16.662, 1.337, 15.806, 14.950),
        PipeThreadRow("G 1/2", 14, 20.955, 1.814, 19.794, 18.633),
        PipeThreadRow("G 5/8", 14, 22.911, 1.814, 21.750, 20.589),
        PipeThreadRow("G 3/4", 14, 26.441, 1.814, 25.280, 24.119),
        PipeThreadRow("G 7/8", 14, 30.201, 1.814, 29.040, 27.879),
        PipeThreadRow("G 1", 11, 33.249, 2.309, 31.770, 30.291),
        PipeThreadRow("G 1-1/4", 11, 41.910, 2.309, 40.431, 38.952),
        PipeThreadRow("G 1-1/2", 11, 47.803, 2.309, 46.324, 44.845),
        PipeThreadRow("G 2", 11, 59.614, 2.309, 58.135, 56.656),
    )

    val diagnostics: List<DiagnosticGuide> = listOf(
        DiagnosticGuide(
            id = "edge_wear",
            symptom = "Износ по задней поверхности",
            description = "Кромка постепенно стирается по задней поверхности; растут силы резания и ухудшается поверхность.",
            likelyCauses = listOf("Высокая скорость резания", "Слишком долгое время контакта", "Недостаточно износостойкая марка"),
            checksFirst = listOf("Сравни фактическую Vc с каталогом пластины", "Проверь, не слишком ли мала подача"),
            actions = listOf("Снизь Vc", "При допустимой нагрузке немного увеличь подачу", "Выбери более износостойкую марку/покрытие"),
            sourceLabel = "Kennametal turning troubleshooting"
        ),
        DiagnosticGuide(
            id = "chipping",
            symptom = "Скол кромки",
            description = "Мелкие фрагменты откалываются от режущей кромки, часто из-за ударов, вибраций или слабой жёсткости.",
            likelyCauses = listOf("Вибрация", "Недостаточная жёсткость", "Слишком хрупкая марка/острая подготовка кромки", "Ударный вход"),
            checksFirst = listOf("Проверь зажим детали и державки", "Сократи вылет", "Проверь биение и состояние посадки пластины"),
            actions = listOf("Поставь более вязкую марку", "Усиль подготовку кромки", "Снизь ударность входа", "Измени угол в плане, если операция позволяет"),
            sourceLabel = "Kennametal turning troubleshooting"
        ),
        DiagnosticGuide(
            id = "built_up_edge",
            symptom = "Нарост на кромке",
            description = "Материал налипает на кромку, портит поверхность и периодически отрывается вместе с частицами инструмента.",
            likelyCauses = listOf("Слишком низкая скорость", "Липкий материал", "Трение вместо уверенного резания", "Недостаточный подвод СОЖ"),
            checksFirst = listOf("Проверь Vc", "Проверь, что подача находится в рабочем диапазоне стружколома", "Осмотри подвод СОЖ"),
            actions = listOf("Подними Vc в допустимом диапазоне", "Используй более острую геометрию", "Улучши СОЖ", "При необходимости выбери подходящую PVD/cermet марку"),
            sourceLabel = "Kennametal turning troubleshooting"
        ),
        DiagnosticGuide(
            id = "crater",
            symptom = "Кратерный износ",
            description = "Углубление на передней поверхности пластины из-за высокой температуры и взаимодействия со стружкой.",
            likelyCauses = listOf("Высокая температура", "Завышенная Vc", "Неподходящая марка"),
            checksFirst = listOf("Проверь Vc и подачу", "Посмотри, куда реально попадает СОЖ"),
            actions = listOf("Снизь Vc", "При необходимости снизь подачу", "Выбери более термостойкую/износостойкую марку", "Улучши подвод СОЖ"),
            sourceLabel = "Kennametal turning troubleshooting"
        ),
        DiagnosticGuide(
            id = "thermal",
            symptom = "Перегрев / тепловая деформация",
            description = "Кромка теряет форму под высокой температурой и давлением.",
            likelyCauses = listOf("Высокая Vc", "Слишком большая подача", "Большой DOC", "Марка плохо держит горячую твёрдость"),
            checksFirst = listOf("Оцени Vc + f + ap как систему", "Проверь стабильность охлаждения"),
            actions = listOf("Снизь Vc", "Уменьши f и/или ap", "Выбери марку для большей тепловой нагрузки"),
            sourceLabel = "Kennametal turning troubleshooting"
        ),
        DiagnosticGuide(
            id = "vibration",
            symptom = "Вибрация / рябь",
            description = "Самовозбуждающиеся колебания дают характерные волны, шум и нестабильный размер.",
            likelyCauses = listOf("Большой вылет", "Слабый зажим", "Слишком большой радиус вершины", "Неблагоприятная скорость"),
            checksFirst = listOf("Сократи вылет инструмента/детали", "Проверь патрон, кулачки и опоры", "Проверь радиус вершины"),
            actions = listOf("Снизь скорость на небольшой шаг", "Попробуй изменить подачу, чтобы уйти из резонанса", "Поставь меньший радиус вершины", "Добавь поддержку детали"),
            sourceLabel = "Machining practice + rigidity-first troubleshooting"
        ),
        DiagnosticGuide(
            id = "size_drift",
            symptom = "Размер уходит / конусность",
            description = "Размер меняется по длине детали или от детали к детали.",
            likelyCauses = listOf("Тепловой уход", "Отжатие", "Износ пластины", "Недостаточная соосность/жёсткость"),
            checksFirst = listOf("Сравни холодный и прогретый размер", "Проверь износ кромки", "Проверь вылет и опору длинной детали"),
            actions = listOf("Стабилизируй чистовой припуск", "Уменьши вылет", "Добавь поддержку", "Корректируй только после определения причины"),
            sourceLabel = "Machining practice"
        )
    )

    val toolReferences: List<ToolReference> = listOf(
        ToolReference("CNMG / WNMG", "Черновая и получистовая", listOf("Прочная отрицательная геометрия", "Хорошо переносит силовой рез", "Требует более жёсткой системы и большей мощности")),
        ToolReference("CCMT / DCMT", "Чистовая и расточка", listOf("Положительная геометрия", "Меньше силы резания", "Подходит для тонких деталей и расточных операций")),
        ToolReference("VNMG / DNMG", "Профиль и плечи", listOf("Удобный доступ к сложному профилю", "Острый угол требует аккуратности на прерывистом резе", "Минимизируй вылет")),
        ToolReference("Радиус вершины", "R0.2 / R0.4 / R0.8", listOf("Меньший радиус уменьшает силы и риск вибрации", "R0.4 — универсальный старт", "Больший радиус прочнее, но повышает радиальную силу")),
        ToolReference("16ER / 16IR", "Резьбовые пластины", listOf("ER — наружная, IR — внутренняя", "Full profile рассчитан на конкретный шаг", "Partial profile универсальнее, но не формирует вершины полностью")),
        ToolReference("Расточная державка", "Жёсткость прежде всего", listOf("Минимизируй вылет", "Максимизируй диаметр державки в пределах отверстия", "При ряби сначала исправляй жёсткость, а не только режим")),
    )
}

object CuttingAdvisor {
    fun recommend(
        material: MaterialPreset,
        operation: OperationType,
        strategy: Strategy,
        diameterMm: Double,
        insertRadiusMm: Double = 0.4,
    ): CuttingRecommendation {
        require(diameterMm > 0.0) { "Диаметр должен быть больше нуля" }

        val strategyFactor = when (strategy) {
            Strategy.STABLE -> 0.22
            Strategy.BALANCED -> 0.48
            Strategy.PRODUCTIVE -> 0.72
        }

        var vc = lerp(material.vcMin, material.vcMax, strategyFactor)
        var feed = lerp(material.feedMin, material.feedMax, strategyFactor)
        var ap = lerp(material.apMin, material.apMax, strategyFactor)

        when (operation) {
            OperationType.ROUGH -> {
                vc *= 0.92
                feed *= 1.12
                ap *= 1.05
            }
            OperationType.FINISH -> {
                vc *= 1.05
                feed *= 0.48
                ap *= 0.35
            }
            OperationType.BORING -> {
                vc *= 0.86
                feed *= 0.72
                ap *= 0.60
            }
            OperationType.GROOVING -> {
                vc *= 0.72
                feed *= 0.55
                ap *= 0.45
            }
            OperationType.PARTING -> {
                vc *= 0.62
                feed *= 0.52
                ap *= 0.35
            }
        }

        vc = vc.coerceIn(material.vcMin * 0.60, material.vcMax)
        feed = feed.coerceIn(material.feedMin * 0.65, material.feedMax)
        ap = ap.coerceIn(0.10, material.apMax)

        val rpm = ((1000.0 * vc) / (PI * diameterMm)).roundToInt().coerceAtLeast(1)
        val feedRate = (feed * rpm).roundToInt().coerceAtLeast(1)

        val warnings = buildList {
            add("Стартовая рекомендация, а не каталожный режим конкретной пластины.")
            if (diameterMm < 8.0) add("Малый диаметр: проверь ограничение максимальных оборотов и зажим детали.")
            if (insertRadiusMm >= 0.8 && operation == OperationType.FINISH) add("R${insertRadiusMm}: на слабой системе большой радиус может вызвать вибрацию.")
            if (operation == OperationType.PARTING || operation == OperationType.GROOVING) add("Для канавки/отрезки обязательно сверяй подачу с шириной и геометрией конкретной пластины.")
            if (material.id == "30HGSA_HARD") add("Для 30ХГСА 35–40 HRC сначала проверь фактическую твёрдость и состояние реза: непрерывный или прерывистый.")
        }

        return CuttingRecommendation(
            material = material,
            operation = operation,
            strategy = strategy,
            diameterMm = diameterMm,
            cuttingSpeedMMin = round3(vc),
            rpm = rpm,
            feedPerRevMm = round3(feed),
            feedRateMmMin = feedRate,
            depthOfCutMm = round3(ap),
            warnings = warnings,
        )
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.coerceIn(0.0, 1.0)
}

object ThreadCalculator {
    fun metricBasic(nominalMm: Double, pitchMm: Double): MetricThreadGeometry {
        require(nominalMm > 0.0) { "Номинальный диаметр должен быть больше нуля" }
        require(pitchMm > 0.0) { "Шаг должен быть больше нуля" }
        require(pitchMm < nominalMm) { "Шаг должен быть меньше номинального диаметра" }

        return MetricThreadGeometry(
            nominalMm = nominalMm,
            pitchMm = pitchMm,
            pitchDiameterBasicMm = round3(nominalMm - 0.6495190528 * pitchMm),
            externalMinorBasicMm = round3(nominalMm - 1.226869 * pitchMm),
            internalMinorBasicMm = round3(nominalMm - 1.082532 * pitchMm),
            tapDrillApproxMm = round2(nominalMm - pitchMm),
            radialThreadHeightMm = round3(0.6134345 * pitchMm),
        )
    }

    fun standardCoarsePitch(nominalMm: Double): Double? = CncCatalog.coarsePitchByNominal[nominalMm]
}

object FanucThreadGenerator {
    fun generate(input: FanucThreadInput): FanucProgramResult {
        require(input.nominalDiameterMm > 0.0) { "Диаметр должен быть больше нуля" }
        require(input.pitchMm > 0.0) { "Шаг должен быть больше нуля" }
        require(input.finishPasses in 1..99) { "Чистовых проходов должно быть 1–99" }
        require(input.chamferTenthsLead in 0..99) { "Фаска G76 должна быть 0–99" }

        val g = ThreadCalculator.metricBasic(input.nominalDiameterMm, input.pitchMm)
        val finalX = when (input.side) {
            ThreadSide.EXTERNAL -> g.externalMinorBasicMm
            ThreadSide.INTERNAL -> g.nominalMm
        }

        val pPacked = "%02d%02d%02d".format(
            input.finishPasses,
            input.chamferTenthsLead,
            60,
        )
        val qMinMicron = mmToMicronWord(input.minCutMm)
        val pHeightMicron = mmToMicronWord(g.radialThreadHeightMm)
        val qFirstMicron = mmToMicronWord(input.firstCutMm)

        val g76 = buildString {
            appendLine("(CNC MASTER v0.2 — TEMPLATE, VERIFY ON MACHINE)")
            appendLine("G97 (SET SAFE RPM BEFORE THREADING)")
            appendLine("G76 P$pPacked Q$qMinMicron R${fmt(input.finishAllowanceMm, 3)}")
            append("G76 X${fmt(finalX, 3)} Z${fmt(input.zEndMm, 3)} P$pHeightMicron Q$qFirstMicron F${fmt(input.pitchMm, 3)}")
        }

        val g92Passes = generateG92PassDiameters(input, g)
        val g92 = buildString {
            appendLine("(CNC MASTER v0.2 — G92 TEMPLATE)")
            appendLine("(START FROM A SAFE X/Z POSITION; USE G97)")
            g92Passes.forEach { x ->
                appendLine("G92 X${fmt(x, 3)} Z${fmt(input.zEndMm, 3)} F${fmt(input.pitchMm, 3)}")
            }
            repeat(input.finishPasses.coerceAtMost(4)) {
                appendLine("G92 X${fmt(finalX, 3)} Z${fmt(input.zEndMm, 3)} F${fmt(input.pitchMm, 3)}")
            }
        }.trimEnd()

        val warnings = buildList {
            add("Fanuc 0i-TF поддерживает G92 и двухстрочный G76, но конкретные параметры станка и опции ЧПУ нужно сверить с руководством станка.")
            add("Перед первым запуском: Single Block + Dry Run/воздух + безопасный отвод + низкие обороты.")
            add("Расчёт X основан на базовом профиле метрической 60° резьбы; размер по калибру корректируется допуском и износом инструмента.")
            if (input.side == ThreadSide.INTERNAL) add("Для внутренней резьбы X=${fmt(finalX, 3)} — базовый большой диаметр. Проверь предрасточку и поле допуска.")
            if (input.hand == ThreadHand.LEFT) add("Левая резьба: направление шпинделя и подход инструмента зависят от ориентации державки. CNC Master намеренно не подставляет M03/M04 автоматически.")
        }

        return FanucProgramResult(
            g76 = g76,
            g92 = g92,
            calculatedFinalX = finalX,
            radialHeight = g.radialThreadHeightMm,
            warnings = warnings,
        )
    }

    private fun generateG92PassDiameters(
        input: FanucThreadInput,
        geometry: MetricThreadGeometry,
        passes: Int = 7,
    ): List<Double> {
        val totalRadial = geometry.radialThreadHeightMm
        return (1..passes).map { i ->
            val fraction = sqrt(i.toDouble() / passes.toDouble())
            val radial = totalRadial * fraction
            when (input.side) {
                ThreadSide.EXTERNAL -> round3(input.nominalDiameterMm - 2.0 * radial)
                ThreadSide.INTERNAL -> round3(geometry.internalMinorBasicMm + 2.0 * radial)
            }
        }.dropLast(1) + listOf(
            when (input.side) {
                ThreadSide.EXTERNAL -> geometry.externalMinorBasicMm
                ThreadSide.INTERNAL -> geometry.nominalMm
            }
        )
    }

    private fun mmToMicronWord(mm: Double): Int = (mm.coerceAtLeast(0.0) * 1000.0).roundToInt()
}

private fun fmt(value: Double, decimals: Int): String = "% .${decimals}f".format(java.util.Locale.US, value).trim()
private fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0
private fun round3(value: Double): Double = (value * 1000.0).roundToInt() / 1000.0
