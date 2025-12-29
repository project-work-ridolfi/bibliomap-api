package it.unipegaso.api.dto;

import java.util.List;

public record ChartData(List<String> labels, List<Long> data) {}