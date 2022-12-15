package tech.fearless.purpledatahacks

data class ClassificationMessage(val sendingUser: String, val message: String)
data class PotentiallySexistMessage(val sendingUser: String, val message: String, val confidence: Float)