package com.microservice.utilities.data.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.SqlReturnResultSet;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for executing stored procedures with parameter mapping and result handling.
 */
@Component
public class StoredProcedureExecutor {

    private static final Logger logger = LoggerFactory.getLogger(StoredProcedureExecutor.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public StoredProcedureExecutor(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    /**
     * Execute stored procedure with input parameters only
     */
    public void executeStoredProcedure(String procedureName, Map<String, Object> inputParams) {
        logger.debug("Executing stored procedure: {} with parameters: {}", procedureName, inputParams);
        
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withProcedureName(procedureName);
        
        try {
            jdbcCall.execute(inputParams);
            logger.debug("Successfully executed stored procedure: {}", procedureName);
        } catch (Exception e) {
            logger.error("Error executing stored procedure: {}", procedureName, e);
            throw new RuntimeException("Failed to execute stored procedure: " + procedureName, e);
        }
    }

    /**
     * Execute stored procedure and return result map
     */
    public Map<String, Object> executeStoredProcedureWithResult(String procedureName, 
                                                               Map<String, Object> inputParams) {
        logger.debug("Executing stored procedure with result: {} with parameters: {}", 
                    procedureName, inputParams);
        
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withProcedureName(procedureName);
        
        try {
            Map<String, Object> result = jdbcCall.execute(inputParams);
            logger.debug("Successfully executed stored procedure: {} with result keys: {}", 
                        procedureName, result.keySet());
            return result;
        } catch (Exception e) {
            logger.error("Error executing stored procedure with result: {}", procedureName, e);
            throw new RuntimeException("Failed to execute stored procedure: " + procedureName, e);
        }
    }

    /**
     * Execute stored procedure with output parameters
     */
    public Map<String, Object> executeStoredProcedureWithOutputParams(String procedureName,
                                                                     Map<String, Object> inputParams,
                                                                     Map<String, Integer> outputParams) {
        logger.debug("Executing stored procedure with output params: {} with input: {} and output: {}", 
                    procedureName, inputParams, outputParams);
        
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withProcedureName(procedureName);
        
        // Declare output parameters
        for (Map.Entry<String, Integer> outputParam : outputParams.entrySet()) {
            jdbcCall.declareParameters(new SqlParameter(outputParam.getKey(), outputParam.getValue()));
        }
        
        try {
            Map<String, Object> result = jdbcCall.execute(inputParams);
            logger.debug("Successfully executed stored procedure with output params: {}", procedureName);
            return result;
        } catch (Exception e) {
            logger.error("Error executing stored procedure with output params: {}", procedureName, e);
            throw new RuntimeException("Failed to execute stored procedure: " + procedureName, e);
        }
    }

    /**
     * Execute stored procedure that returns a result set
     */
    public <T> List<T> executeStoredProcedureWithResultSet(String procedureName,
                                                          Map<String, Object> inputParams,
                                                          ResultSetMapper<T> mapper) {
        logger.debug("Executing stored procedure with result set: {} with parameters: {}", 
                    procedureName, inputParams);
        
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withProcedureName(procedureName)
                .returningResultSet("result", (rs, rowNum) -> mapper.mapRow(rs, rowNum));
        
        try {
            Map<String, Object> result = jdbcCall.execute(inputParams);
            @SuppressWarnings("unchecked")
            List<T> resultList = (List<T>) result.get("result");
            
            logger.debug("Successfully executed stored procedure with result set: {}, returned {} rows", 
                        procedureName, resultList != null ? resultList.size() : 0);
            
            return resultList != null ? resultList : new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error executing stored procedure with result set: {}", procedureName, e);
            throw new RuntimeException("Failed to execute stored procedure: " + procedureName, e);
        }
    }

    /**
     * Execute stored function (returns a single value)
     */
    public <T> T executeStoredFunction(String functionName, 
                                     Map<String, Object> inputParams,
                                     Class<T> returnType) {
        logger.debug("Executing stored function: {} with parameters: {}", functionName, inputParams);
        
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withFunctionName(functionName);
        
        try {
            T result = jdbcCall.executeFunction(returnType, inputParams);
            logger.debug("Successfully executed stored function: {} with result: {}", functionName, result);
            return result;
        } catch (Exception e) {
            logger.error("Error executing stored function: {}", functionName, e);
            throw new RuntimeException("Failed to execute stored function: " + functionName, e);
        }
    }

    /**
     * Execute complex stored procedure with custom callable statement
     */
    public <T> T executeComplexStoredProcedure(String sql, 
                                              ComplexProcedureCallback<T> callback) {
        logger.debug("Executing complex stored procedure with SQL: {}", sql);
        
        try {
            return jdbcTemplate.execute(sql, (CallableStatementCallback<T>) callback::execute);
        } catch (Exception e) {
            logger.error("Error executing complex stored procedure", e);
            throw new RuntimeException("Failed to execute complex stored procedure", e);
        }
    }

    /**
     * Execute stored procedure with multiple result sets
     */
    public Map<String, List<Map<String, Object>>> executeStoredProcedureWithMultipleResultSets(
            String procedureName, Map<String, Object> inputParams, String... resultSetNames) {
        
        logger.debug("Executing stored procedure with multiple result sets: {} with parameters: {}", 
                    procedureName, inputParams);
        
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withProcedureName(procedureName);
        
        // Add result set declarations
        for (String resultSetName : resultSetNames) {
            jdbcCall.declareParameters(new SqlReturnResultSet(resultSetName, 
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    int columnCount = rs.getMetaData().getColumnCount();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rs.getMetaData().getColumnName(i);
                        row.put(columnName, rs.getObject(i));
                    }
                    return row;
                }));
        }
        
        try {
            Map<String, Object> result = jdbcCall.execute(inputParams);
            Map<String, List<Map<String, Object>>> resultSets = new HashMap<>();
            
            for (String resultSetName : resultSetNames) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get(resultSetName);
                resultSets.put(resultSetName, resultSet != null ? resultSet : new ArrayList<>());
            }
            
            logger.debug("Successfully executed stored procedure with multiple result sets: {}", procedureName);
            return resultSets;
        } catch (Exception e) {
            logger.error("Error executing stored procedure with multiple result sets: {}", procedureName, e);
            throw new RuntimeException("Failed to execute stored procedure: " + procedureName, e);
        }
    }

    /**
     * Execute stored procedure with transaction control
     */
    public <T> T executeStoredProcedureInTransaction(String procedureName,
                                                   Map<String, Object> inputParams,
                                                   StoredProcedureCallback<T> callback) {
        logger.debug("Executing stored procedure in transaction: {}", procedureName);
        
        return jdbcTemplate.execute((CallableStatementCallback<T>) cs -> {
            try {
                // Set input parameters
                int paramIndex = 1;
                for (Object param : inputParams.values()) {
                    cs.setObject(paramIndex++, param);
                }
                
                // Execute and handle result
                boolean hasResultSet = cs.execute();
                return callback.handleResult(cs, hasResultSet);
                
            } catch (SQLException e) {
                logger.error("SQL error in stored procedure transaction: {}", procedureName, e);
                throw e;
            }
        });
    }

    /**
     * Build stored procedure call SQL with parameter placeholders
     */
    public String buildStoredProcedureCall(String procedureName, int parameterCount) {
        StringBuilder sql = new StringBuilder();
        sql.append("{call ").append(procedureName).append("(");
        
        for (int i = 0; i < parameterCount; i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        
        sql.append(")}");
        return sql.toString();
    }

    /**
     * Build stored function call SQL
     */
    public String buildStoredFunctionCall(String functionName, int parameterCount) {
        StringBuilder sql = new StringBuilder();
        sql.append("{? = call ").append(functionName).append("(");
        
        for (int i = 0; i < parameterCount; i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        
        sql.append(")}");
        return sql.toString();
    }

    /**
     * Interface for mapping result set rows
     */
    @FunctionalInterface
    public interface ResultSetMapper<T> {
        T mapRow(ResultSet rs, int rowNum) throws SQLException;
    }

    /**
     * Interface for complex stored procedure callbacks
     */
    @FunctionalInterface
    public interface ComplexProcedureCallback<T> {
        T execute(CallableStatement cs) throws SQLException;
    }

    /**
     * Interface for stored procedure callbacks with transaction control
     */
    @FunctionalInterface
    public interface StoredProcedureCallback<T> {
        T handleResult(CallableStatement cs, boolean hasResultSet) throws SQLException;
    }

    /**
     * Utility class for common SQL types mapping
     */
    public static class SqlTypes {
        public static final Map<Class<?>, Integer> TYPE_MAP = new HashMap<>();
        
        static {
            TYPE_MAP.put(String.class, Types.VARCHAR);
            TYPE_MAP.put(Integer.class, Types.INTEGER);
            TYPE_MAP.put(int.class, Types.INTEGER);
            TYPE_MAP.put(Long.class, Types.BIGINT);
            TYPE_MAP.put(long.class, Types.BIGINT);
            TYPE_MAP.put(Double.class, Types.DOUBLE);
            TYPE_MAP.put(double.class, Types.DOUBLE);
            TYPE_MAP.put(Float.class, Types.FLOAT);
            TYPE_MAP.put(float.class, Types.FLOAT);
            TYPE_MAP.put(Boolean.class, Types.BOOLEAN);
            TYPE_MAP.put(boolean.class, Types.BOOLEAN);
            TYPE_MAP.put(java.sql.Date.class, Types.DATE);
            TYPE_MAP.put(java.sql.Time.class, Types.TIME);
            TYPE_MAP.put(java.sql.Timestamp.class, Types.TIMESTAMP);
            TYPE_MAP.put(java.time.LocalDate.class, Types.DATE);
            TYPE_MAP.put(java.time.LocalDateTime.class, Types.TIMESTAMP);
        }
        
        public static int getSqlType(Class<?> javaType) {
            return TYPE_MAP.getOrDefault(javaType, Types.OTHER);
        }
    }
}