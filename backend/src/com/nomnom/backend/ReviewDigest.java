package com.nomnom.backend;

import java.util.List;

public record ReviewDigest(List<String> bestFor, List<String> pros, List<String> cons) {}
