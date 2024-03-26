import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

interface ClassifiedText {
    val text: String
    val isSexist: Boolean
}

@JsonPropertyOrder("sentences", "label")
data class OriginalCommentDataIsep(val sentences: String, val label: Int): ClassifiedText {
    override val text: String
        get() = sentences
    override val isSexist: Boolean
        get() = label == 1
}

@JsonPropertyOrder("id", "dataset", "text", "toxicity", "sexist", "of_id")
data class OriginalCommentDataGesis(val id: Int, val dataset: String, override val text: String, val toxicity: Double, val sexist: Boolean, val of_id: Int): ClassifiedText {
    override val isSexist: Boolean
        get() = sexist
}

data class Classification(val displayName: String)
data class PreparedData(val textContent: String, val classificationAnnotation: Classification)

inline fun <reified  InputSchema: ClassifiedText> csvToJsonL(
    inputFile: File,
    outputFile: File,
    csvMapper: CsvMapper,
    jsonMapper: ObjectMapper,
    filter: (InputSchema) -> Boolean = { _ -> true }
) {
    val ingestSchema = csvMapper.schemaFor(InputSchema::class.java)
    val reader = csvMapper.readerFor(InputSchema::class.java)
        .with(ingestSchema)
        .readValues<InputSchema>(inputFile)

    if (!outputFile.exists()) outputFile.createNewFile()
    outputFile.writer().use { writer ->
        for (comment in reader) {
            if (!filter(comment)) continue
            val jsonified = PreparedData(
                textContent = comment.text,
                classificationAnnotation = Classification(if (comment.isSexist) "sexist" else "non-sexist")
            )

            writer.write(jsonMapper.writeValueAsString(jsonified) + "\n")
        }
    }
}

fun main() {
    val originalInputIsep = File("./DataPrep/src/main/resources/ISEP Sexist Data Labeling.csv")
    val originalInputGesis = File("./DataPrep/src/main/resources/GESIS_sexism_data.csv")

    val outputFileIsep = File("./DataPrep/src/main/resources/training_data.jsonl")
    val outputFileGesis = File("./DataPrep/src/main/resources/training_data_gesis.jsonl")

    val jsonMapper = jacksonObjectMapper()
    val csvMapper = CsvMapper().registerKotlinModule() as CsvMapper

    csvToJsonL<OriginalCommentDataIsep>(originalInputIsep, outputFileIsep, csvMapper, jsonMapper)
    csvToJsonL<OriginalCommentDataGesis>(originalInputGesis, outputFileGesis, csvMapper, jsonMapper) { it.of_id == -1 }
}